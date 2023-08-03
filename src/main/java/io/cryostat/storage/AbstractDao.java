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
package io.cryostat.storage;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;

import io.cryostat.core.log.Logger;

public abstract class AbstractDao<I, T> {

    protected final Class<T> klazz;
    protected final EntityManager entityManager;
    protected final Logger logger;

    protected AbstractDao(Class<T> klazz, EntityManager entityManager, Logger logger) {
        this.klazz = klazz;
        this.entityManager = entityManager;
        this.logger = logger;
    }

    public final T save(T t) {
        synchronized (entityManager) {
            Objects.requireNonNull(t);
            EntityTransaction transaction = entityManager.getTransaction();
            try {
                transaction.begin();
                entityManager.persist(t);
                transaction.commit();
                entityManager.detach(t);
                return t;
            } catch (Exception e) {
                if (transaction != null) {
                    transaction.rollback();
                }
                logger.error(e);
                throw e;
            }
        }
    }

    public final boolean delete(I id) {
        synchronized (entityManager) {
            Objects.requireNonNull(id);
            EntityTransaction transaction = entityManager.getTransaction();
            try {
                transaction.begin();
                T t = entityManager.find(klazz, id);
                entityManager.remove(t);
                transaction.commit();
                return true;
            } catch (Exception e) {
                if (transaction != null) {
                    transaction.rollback();
                }
                logger.error(e);
                return false;
            }
        }
    }

    public final Optional<T> get(I id) {
        synchronized (entityManager) {
            Objects.requireNonNull(id);
            EntityTransaction transaction = entityManager.getTransaction();
            try {
                T t = entityManager.find(klazz, id);
                if (t != null) {
                    entityManager.detach(t);
                }
                return Optional.ofNullable(t);
            } catch (Exception e) {
                if (transaction != null) {
                    transaction.rollback();
                }
                throw e;
            }
        }
    }

    public final List<T> getAll() {
        synchronized (entityManager) {
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery<T> cq = cb.createQuery(klazz);
            Root<T> rootEntry = cq.from(klazz);
            CriteriaQuery<T> all = cq.select(rootEntry);
            TypedQuery<T> allQuery = entityManager.createQuery(all);
            return allQuery.getResultList();
        }
    }
}
