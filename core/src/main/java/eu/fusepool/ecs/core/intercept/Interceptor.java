/*
 * Copyright 2013 Reto.
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
package eu.fusepool.ecs.core.intercept;

import eu.fusepool.ecs.core.Query;
import org.apache.clerezza.commons.rdf.IRI;
import org.apache.clerezza.rdf.utils.GraphNode;

/**
 * An interceptor can intercept the query and the result of an ECS query request.
 */
public interface Interceptor {
    
    /**
     * this allows the interceptor to modify an incoming query
     */
    public Query interceptQuery(final Query query);

    /**
     * this allows the interceptor to modify the result generate for a query
     * 
     * @param query the query as it was processed by ECS after interceptors' modifications
     * @param result the intercepted result, ECS will return the return value of this method instead of this 
     */
    public GraphNode interceptResult(final Query query, final GraphNode result);

    /**
     * Notifies the interceptor that metdata for a particular IRI has been requested
     * @param iri 
     */
    public void notifyMetaRequest(IRI iri);
    
}
