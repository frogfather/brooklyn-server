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
package org.apache.brooklyn.core.mgmt.persist;

/**
 * Loads the class-renames from the configuration file on the classpath.
 *
 * @see {@link #DESERIALIZING_CLASS_RENAMES_PROPERTIES_PATH}
 */
public class ClasspathConfigLoader extends PropertiesConfigLoader {

    private static final String DESERIALIZING_CLASS_RENAMES_PROPERTIES_PATH = "classpath://org/apache/brooklyn/core/mgmt/persist/deserializingClassRenames.properties";

    public ClasspathConfigLoader(){
        super(DESERIALIZING_CLASS_RENAMES_PROPERTIES_PATH);
    }

}
