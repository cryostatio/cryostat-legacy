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
package io.cryostat.discovery;

import java.net.URI;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;

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

    public PluginInfo save(String realm, URI callback, EnvironmentNode subtree) {
        Objects.requireNonNull(realm);
        Objects.requireNonNull(subtree);
        return super.save(new PluginInfo(realm, callback, gson.toJson(subtree)));
    }

    public PluginInfo update(UUID id, Set<? extends AbstractNode> children) {
        Objects.requireNonNull(id);
        Objects.requireNonNull(children);
        PluginInfo plugin = get(id).orElseThrow(() -> new NoSuchElementException(id.toString()));
        EnvironmentNode original = gson.fromJson(plugin.getSubtree(), EnvironmentNode.class);

        EnvironmentNode subtree =
                new EnvironmentNode(
                        original.getName(), original.getNodeType(), original.getLabels());
        subtree.addChildren(children == null ? Set.of() : children);

        EntityTransaction transaction = entityManager.getTransaction();
        try {
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
