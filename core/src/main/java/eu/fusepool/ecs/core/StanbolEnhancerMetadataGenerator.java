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

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.ws.rs.core.MediaType;
import org.apache.clerezza.rdf.core.UriRef;
import org.apache.clerezza.rdf.metadata.MetaDataGenerator;
import org.apache.clerezza.rdf.utils.GraphNode;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.stanbol.enhancer.servicesapi.Chain;
import org.apache.stanbol.enhancer.servicesapi.ChainManager;
import org.apache.stanbol.enhancer.servicesapi.ContentItem;
import org.apache.stanbol.enhancer.servicesapi.ContentItemFactory;
import org.apache.stanbol.enhancer.servicesapi.ContentSource;
import org.apache.stanbol.enhancer.servicesapi.EnhancementException;
import org.apache.stanbol.enhancer.servicesapi.EnhancementJobManager;
import org.apache.stanbol.enhancer.servicesapi.impl.ByteArraySource;

@Component
@Service(MetaDataGenerator.class)
public class StanbolEnhancerMetadataGenerator implements MetaDataGenerator {

    @Reference
    private EnhancementJobManager enhancementJobManager;
    @Reference
    private ChainManager chainManager;
    @Reference
    private ContentItemFactory contentItemFactory;

    public void generate(GraphNode node, byte[] data, MediaType mediaType) {
        System.out.println("generating metadata");
        try {
            final ContentSource contentSource = new ByteArraySource(
                    data, mediaType.toString());
            final ContentItem contentItem = contentItemFactory.createContentItem(
                    (UriRef) node.getNode(), contentSource);
            final String chainName = "default";
            final Chain chain = chainManager.getChain(chainName);
            if (chain == null) {
                throw new RuntimeException("No chain by that name: " + chainName);
            }
            enhancementJobManager.enhanceContent(contentItem, chain);
            //TODO extract only important data
            node.getGraph().addAll(contentItem.getMetadata());
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        } catch (EnhancementException ex) {
            throw new RuntimeException(ex);
        }
    }
}
