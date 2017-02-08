/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.plugin.management.internal;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.plugin.repository.PluginRepositoriesSpec;
import org.gradle.util.ConfigureUtil;

public class DefaultPluginManagementSpec implements InternalPluginManagementSpec {

    private final PluginRepositoriesSpec delegate;
    private final InternalPluginResolutionStrategy pluginResolutionStrategy;

    public DefaultPluginManagementSpec(PluginRepositoriesSpec delegate, InternalPluginResolutionStrategy pluginResolutionStrategy) {
        this.delegate = delegate;
        this.pluginResolutionStrategy = pluginResolutionStrategy;
    }

    @Override
    public void repositories(Action<? super PluginRepositoriesSpec> repositoriesAction) {
        repositoriesAction.execute(delegate);
    }

    @Override
    public void repositories(Closure closure) {
        ConfigureUtil.configure(closure, delegate);
    }

    @Override
    public InternalPluginResolutionStrategy getPluginResolutionStrategy() {
        return pluginResolutionStrategy;
    }

}