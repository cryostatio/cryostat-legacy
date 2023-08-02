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
package io.cryostat.discovery;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;

import io.cryostat.core.log.Logger;
import io.cryostat.platform.discovery.AbstractNode;
import io.cryostat.platform.discovery.EnvironmentNode;
import io.cryostat.storage.AbstractDao;

import com.google.gson.Gson;

class PluginInfoDao extends AbstractDao<UUID, PluginInfo> {

    private final Gson gson;

    PluginInfoDao(EntityManager em, Gson gson, Logger logger) {
        super(PluginInfo.class, em, logger);
        this.gson = gson;
    }

    public final PluginInfo save(String realm, URI callback, EnvironmentNode subtree) {
        synchronized (entityManager) {
            Objects.requireNonNull(realm);
            Objects.requireNonNull(subtree);
            return super.save(new PluginInfo(realm, callback, gson.toJson(subtree)));
        }
    }

    public final List<PluginInfo> getByRealm(String realm) {
        synchronized (entityManager) {
            Objects.requireNonNull(realm);

            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery<PluginInfo> cq = cb.createQuery(klazz);
            Root<PluginInfo> rootEntry = cq.from(klazz);
            CriteriaQuery<PluginInfo> all = cq.select(rootEntry);
            CriteriaQuery<PluginInfo> withRealm =
                    all.where(cb.equal(rootEntry.get("realm"), realm));
            TypedQuery<PluginInfo> realmQuery = entityManager.createQuery(withRealm);

            return realmQuery.getResultList();
        }
    }

    public final PluginInfo update(UUID id, EnvironmentNode subtree) {
        synchronized (entityManager) {
            Objects.requireNonNull(id);
            Objects.requireNonNull(subtree);
            EntityTransaction transaction = entityManager.getTransaction();
            try {
                PluginInfo plugin =
                        get(id).orElseThrow(() -> new NoSuchElementException(id.toString()));

                transaction.begin();
                plugin.setSubtree(gson.toJson(subtree));
                entityManager.merge(plugin);
                transaction.commit();
                entityManager.detach(plugin);

                return plugin;
            } catch (Exception e) {
                if (transaction != null) {
                    transaction.rollback();
                }
                logger.error(e);
                throw e;
            }
        }
    }

    public final PluginInfo update(UUID id, Collection<? extends AbstractNode> children) {
        synchronized (entityManager) {
            Objects.requireNonNull(id);
            Objects.requireNonNull(children);
            EntityTransaction transaction = entityManager.getTransaction();
            try {
                PluginInfo plugin =
                        get(id).orElseThrow(() -> new NoSuchElementException(id.toString()));
                EnvironmentNode original =
                        gson.fromJson(plugin.getSubtree(), EnvironmentNode.class);

                EnvironmentNode subtree =
                        new EnvironmentNode(
                                original.getName(),
                                original.getNodeType(),
                                original.getLabels(),
                                children);

                transaction.begin();
                plugin.setSubtree(gson.toJson(subtree));
                entityManager.merge(plugin);
                transaction.commit();
                entityManager.detach(plugin);

                return plugin;
            } catch (Exception e) {
                if (transaction != null) {
                    transaction.rollback();
                }
                logger.error(e);
                throw e;
            }
        }
    }
}
