/*
 * Copyright The Cryostat Authors
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or data
 * (collectively the "Software"), free of charge and under any and all copyright
 * rights in the Software, and any and all patent rights owned or freely
 * licensable by each licensor hereunder covering either (i) the unmodified
 * Software as contributed to or provided by such licensor, or (ii) the Larger
 * Works (as defined below), to deal in both
 *
 * (a) the Software, and
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software (each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 * The above copyright notice and either this complete permission notice or at
 * a minimum a reference to the UPL must be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
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

    public final T update(T t) {
        synchronized (entityManager) {
            Objects.requireNonNull(t);
            EntityTransaction transaction = entityManager.getTransaction();
            try {
                transaction.begin();
                entityManager.merge(t);
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
