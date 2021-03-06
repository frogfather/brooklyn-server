/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.brooklyn.enricher.stock;

import java.util.Map;

import org.apache.brooklyn.api.catalog.Catalog;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntityLocal;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.api.sensor.Sensor;
import org.apache.brooklyn.api.sensor.SensorEvent;
import org.apache.brooklyn.api.sensor.SensorEventListener;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.enricher.AbstractEnricher;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.lifecycle.ServiceStateLogic;
import org.apache.brooklyn.util.core.flags.SetFromFlag;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.common.reflect.TypeToken;

/**
 * Enricher which updates an entry in a sensor map ({@link #TARGET_SENSOR}) 
 * based on the value of another sensor ({@link #SOURCE_SENSOR}.
 * <p>
 * The key used defaults to the name of the source sensor but can be specified with {@link #KEY_IN_TARGET_SENSOR}.
 * The value placed in the map is the result of applying the function in {@link #COMPUTING} to the sensor value,
 * with default behaviour being to remove an entry if <code>null</code> is returned
 * but this can be overriden by setting {@link #REMOVING_IF_RESULT_IS_NULL} false.
 * {@link Entities#REMOVE} and {@link Entities#UNCHANGED} are also respeced as return values for the computation
 * (ignoring generics).
 * Unlike most other enrichers, this defaults to {@link AbstractEnricher#SUPPRESS_DUPLICATES} being true
 *  
 * @author alex
 *
 * @param <S> source sensor type
 * @param <TKey> key type in target sensor map
 * @param <TVal> value type in target sensor map
 */
@SuppressWarnings("serial")
@Catalog(name = "Map Updater", description = "Updates an entry in a sensor Map")
public class UpdatingMap<S,TKey,TVal> extends AbstractEnricher implements SensorEventListener<S> {

    private static final Logger LOG = LoggerFactory.getLogger(UpdatingMap.class);

    public static final ConfigKey<Entity> PRODUCER = ConfigKeys.newConfigKey(Entity.class,
            "enricher.producer",
            "The entity with the trigger sensor (defaults to the enricher's entity)");

    @SetFromFlag("fromSensor")
    public static final ConfigKey<Sensor<?>> SOURCE_SENSOR = ConfigKeys.newConfigKey(
            new TypeToken<Sensor<?>>() {}, 
            "enricher.sourceSensor",
            "The sensor whose change triggers re-evaluation of the target value");
    
    @SetFromFlag("targetSensor")
    public static final ConfigKey<Sensor<?>> TARGET_SENSOR = ConfigKeys.newConfigKey(
            new TypeToken<Sensor<?>>() {}, 
            "enricher.targetSensor",
            "The map-sensor to be updated on the associated entity with the value computed here");

    
    @SetFromFlag("key")
    public static final ConfigKey<Object> KEY_IN_TARGET_SENSOR = ConfigKeys.newConfigKey(
            Object.class, 
            "enricher.updatingMap.keyInTargetSensor",
            "Key to update in the target sensor map, defaulting to the name of the source sensor");

    @SetFromFlag("computing")
    public static final ConfigKey<Function<?, ?>> COMPUTING = ConfigKeys.newConfigKey(
            new TypeToken<Function<?,?>>() {}, 
            "enricher.updatingMap.computing",
            "The function to evaluate the value, which will to be set in the target map-sensor. See 'removingIfResultIsNull' for how null is handled.");
    
    @SetFromFlag("removingIfResultIsNull")
    public static final ConfigKey<Boolean> REMOVING_IF_RESULT_IS_NULL = ConfigKeys.newBooleanConfigKey(
            "enricher.updatingMap.removingIfResultIsNull", 
            "Whether the key in the target map is removed if the result if the computation is null",
            Boolean.TRUE);

    protected Entity producer;
    protected AttributeSensor<S> sourceSensor;
    protected AttributeSensor<Map<TKey,TVal>> targetSensor;
    protected TKey key;
    protected Function<S,? extends TVal> computing;
    protected Boolean removingIfResultIsNull;

    public UpdatingMap() {
    }

    @Override
    public void init() {
        super.init();
        
        // this always suppresses duplicates, but it updates the same map *in place* so the usual suppress duplicates logic should not be applied
        // TODO clean up so that we have synchronization guarantees and can inspect the item to see whether it has changed
        if (Boolean.TRUE.equals(getConfig(SUPPRESS_DUPLICATES))) {
            LOG.warn("suppress-duplicates must not be set on "+this+" because map is updated in-place; unsetting config; will always implicitly suppress duplicates");
            config().set(SUPPRESS_DUPLICATES, (Boolean)null);
        }
    }
    
    @Override
    protected <T> void doReconfigureConfig(ConfigKey<T> key, T val) {
        if (key.getName().equals(SUPPRESS_DUPLICATES.getName())) {
            if (Boolean.TRUE.equals(val)) {
                throw new UnsupportedOperationException("suppress-duplicates must not be set on "+this+" because map is updated in-place; will always implicitly suppress duplicates");
            }
        }
        super.doReconfigureConfig(key, val);
    }
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    public void setEntity(EntityLocal entity) {
        super.setEntity(entity);
        this.producer = getConfig(PRODUCER) == null ? entity: getConfig(PRODUCER);
        this.sourceSensor = (AttributeSensor<S>) getRequiredConfig(SOURCE_SENSOR);
        this.targetSensor = (AttributeSensor<Map<TKey,TVal>>) getRequiredConfig(TARGET_SENSOR);
        this.key = (TKey) getConfig(KEY_IN_TARGET_SENSOR);
        this.computing = (Function) getRequiredConfig(COMPUTING);
        this.removingIfResultIsNull = getConfig(REMOVING_IF_RESULT_IS_NULL);

        subscriptions().subscribe(ImmutableMap.of("notifyOfInitialValue", true), producer, sourceSensor, this);
        
        highlightTriggers(sourceSensor, producer);
    }
    
    @Override
    public void onEvent(SensorEvent<S> event) {
        onUpdated();
    }

    /**
     * Called whenever the values for the set of producers changes (e.g. on an event, or on a member added/removed).
     */
    @SuppressWarnings("unchecked")
    protected void onUpdated() {
        try {
            Object v = computing.apply(producer.getAttribute(sourceSensor));
            if (v == null && Boolean.TRUE.equals(removingIfResultIsNull)) {
                v = Entities.REMOVE;
            }
            if (v == Entities.UNCHANGED) {
                // nothing
            } else {
                TKey key = this.key;
                if (key==null) key = (TKey) sourceSensor.getName();

                ServiceStateLogic.updateMapSensorEntry(entity, targetSensor, key, (TVal) v);
            }
        } catch (Throwable t) {
            LOG.warn("Error calculating map update for enricher "+this, t);
            throw Exceptions.propagate(t);
        }
    }
    
}
