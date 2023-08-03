/*
 * Copyright The Cryostat Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.cryostat.platform.discovery;

import java.util.Set;

import io.cryostat.util.PluggableJsonDeserializer;
import io.cryostat.util.PluggableTypeAdapter;

import dagger.Lazy;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoSet;

@Module
public abstract class PlatformDiscoveryModule {

    @Provides
    @IntoSet
    public static PluggableTypeAdapter<?> provideBaseNodeTypeAdapter() {
        return new BaseNodeTypeAdapter();
    }

    @Provides
    @IntoSet
    static PluggableTypeAdapter<?> provideCustomTargetNodeTypeAdapter() {
        return new CustomTargetNodeTypeAdapter();
    }

    @Provides
    @IntoSet
    static PluggableTypeAdapter<?> provideKubernetesNodeTypeAdapter() {
        return new KubernetesNodeTypeAdapter();
    }

    @Provides
    @IntoSet
    public static PluggableJsonDeserializer<?> provideNodeTypeDeserializer(
            Lazy<Set<PluggableTypeAdapter<?>>> adapters) {
        return new NodeTypeDeserializer(adapters);
    }
}
