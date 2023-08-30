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
package itest.util.http;

public class StoredCredential {
    public int id;
    public String matchExpression;
    public int numMatchingTargets;

    public StoredCredential(int id, String matchExpression, int numMatchingTargets) {
        this.id = id;
        this.matchExpression = matchExpression;
        this.numMatchingTargets = numMatchingTargets;
    }

    public StoredCredential() {}
}
