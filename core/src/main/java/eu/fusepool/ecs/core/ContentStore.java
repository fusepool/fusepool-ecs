/*
 * Copyright 2013 reto.
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
package eu.fusepool.ecs.core;

import java.util.Collection;
import java.util.List;
import org.apache.clerezza.commons.rdf.IRI;
import org.apache.clerezza.rdf.utils.GraphNode;


public interface ContentStore {

    /**
     * Returns a GraphNode of type ContentStoreView.
     *
     * @param contentStoreUri The IRI of the content store to use
     * @param contentStoreViewUri The IRI that shall be assigned to the returned view
     * @param subjects The dc:subjects the matching content items shall have
     * @param searchs The search patterns the matching documents shall satisfy
     * @param items the number of items to return
     * @param offset the position at which to start items (for pagination)
     * @param maxFacets the maximum number of facets the result shall contain
     * @param withContent true if the SIOC:content properties shall be included in the result
     * @return a GraphNode describing the ContentStoreView
     */
    @Deprecated
    GraphNode getContentStoreView(final IRI contentStoreUri, 
            final IRI contentStoreViewUri, final Collection<IRI> subjects, 
            final Collection<String> searchs, 
            Integer items, Integer offset, Integer maxFacets,
            boolean withContent);
    
    @Deprecated
    GraphNode getContentStoreView(final IRI contentStoreUri,
            final IRI contentStoreViewUri,
            final Collection<IRI> subjects,
            final Collection<IRI> types,
            final Collection<String> searchs,
            Integer items,
            Integer offset,
            Integer maxFacets,
            boolean withContent);
    
    GraphNode getContentStoreView(final Query query);
    
}
