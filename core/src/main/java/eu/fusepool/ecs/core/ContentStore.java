package eu.fusepool.ecs.core;

import eu.fusepool.ecs.ontologies.ECS;
import java.security.AccessController;
import java.security.Permission;
import java.security.PrivilegedAction;
import java.util.Collections;
import java.util.Date;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import org.apache.clerezza.jaxrs.utils.TrailingSlash;
import org.apache.clerezza.rdf.core.BNode;
import org.apache.clerezza.rdf.core.MGraph;
import org.apache.clerezza.rdf.core.UriRef;
import org.apache.clerezza.rdf.core.access.EntityAlreadyExistsException;
import org.apache.clerezza.rdf.core.access.TcManager;
import org.apache.clerezza.rdf.core.access.security.TcAccessController;
import org.apache.clerezza.rdf.core.access.security.TcPermission;
import org.apache.clerezza.rdf.core.impl.PlainLiteralImpl;
import org.apache.clerezza.rdf.ontologies.DC;
import org.apache.clerezza.rdf.ontologies.RDF;
import org.apache.clerezza.rdf.ontologies.RDFS;
import org.apache.clerezza.rdf.utils.GraphNode;
import org.apache.clerezza.rdf.utils.UnionMGraph;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.stanbol.commons.indexedgraph.IndexedMGraph;
import org.apache.stanbol.commons.web.viewable.RdfViewable;
import org.apache.stanbol.entityhub.model.clerezza.RdfValueFactory;
import org.apache.stanbol.entityhub.servicesapi.model.Entity;
import org.apache.stanbol.entityhub.servicesapi.model.Representation;
import org.apache.stanbol.entityhub.servicesapi.site.SiteManager;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Uses the SiteManager to resolve entities. Every requested is recorded to
 * a graph. The client gets information and meta-information about the resource
 * and sees all previous requests for that resource.
 */
@Component
@Service(Object.class)
@Property(name="javax.ws.rs", boolValue=true)
@Path("ecs.core")
public class ContentStore {
    
    /**
     * Using slf4j for normal logging
     */
    private static final Logger log = LoggerFactory.getLogger(ContentStore.class);
    
    /**
     * This service allows to get entities from configures sites
     */
    @Reference
    private SiteManager siteManager;
    
    /**
     * This service allows accessing and creating persistent triple collections
     */
    @Reference
    private TcManager tcManager;
    
    /**
     * The graph in which the contents are stored together with digested metadata
     */
    private UriRef REQUEST_LOG_GRAPH_NAME = new UriRef("urn:x-localhost:/ecs.graph");
    
    /**
     * The graph in which the enancer generated enhanceents are stored 
     */
    private UriRef ENHANCEMENT_GRAPH = new UriRef("urn:x-localhost:/ecs.graph");
    
    @Activate
    protected void activate(ComponentContext context) {
        log.info("The example service is being activated");
        try {
            tcManager.createMGraph(REQUEST_LOG_GRAPH_NAME);
            //now make sure everybody can read from the graph
            //or more precisly, anybody who can read the content-graph
            TcAccessController tca = new TcAccessController(tcManager);
            tca.setRequiredReadPermissions(REQUEST_LOG_GRAPH_NAME, 
                    Collections.singleton((Permission)new TcPermission(
                    "urn:x-localinstance:/content.graph", "read")));
        } catch (EntityAlreadyExistsException ex) {
            log.debug("The graph for the request log already exists");
        }
        
    }
    
    @Deactivate
    protected void deactivate(ComponentContext context) {
        log.info("The Enhanced COntent Store");
    }
    
    /**
     * This method return an RdfViewable, this is an RDF serviceUri with associated
     * presentational information.
     */
    @GET
    public RdfViewable serviceEntry(@Context final UriInfo uriInfo, 
            @QueryParam("iri") final UriRef iri, 
            @HeaderParam("user-agent") String userAgent) throws Exception {
        //this maks sure we are nt invoked with a trailing slash which would affect
        //relative resolution of links (e.g. css)
        TrailingSlash.enforcePresent(uriInfo);
        final String resourcePath = uriInfo.getAbsolutePath().toString();
        //The URI at which this service was accessed accessed, this will be the 
        //central serviceUri in the response
        final UriRef serviceUri = new UriRef(resourcePath);
        //the in memory graph to which the triples for the response are added
        final MGraph responseGraph = new IndexedMGraph();
        //A union graph containing both the response specif triples as well 
        //as the log-graph
        final UnionMGraph resultGraph = new UnionMGraph(responseGraph, getRequestLogGraph());
        //This GraphNode represents the service within our result graph
        final GraphNode node = new GraphNode(serviceUri, resultGraph);
        //The triples will be added to the first graph of the union
        //i.e. to the in-memory responseGraph
        //TODO the uri if the view has always params the one of the store is the one without params
        node.addProperty(RDF.type, ECS.ContentStoreView);
        node.addProperty(RDFS.comment, new PlainLiteralImpl("An enhanced content store"));
       
        //What we return is the GraphNode we created with a template path
        return new RdfViewable("ResourceResolver", node, ContentStore.class);
    }
    

    /**
     * Add the description of a serviceUri to the specified MGraph using SiteManager.
     * The description includes the metadata provided by the SiteManager.
     * 
     */
    private void addResourceDescription(UriRef iri, MGraph mGraph) {
        final Entity entity = siteManager.getEntity(iri.getUnicodeString());
        if (entity != null) {
            final RdfValueFactory valueFactory = new RdfValueFactory(mGraph);
            final Representation representation = entity.getRepresentation();
            if (representation != null) {
                valueFactory.toRdfRepresentation(representation);
            }
            final Representation metadata = entity.getMetadata();
            if (metadata != null) {
                valueFactory.toRdfRepresentation(metadata);
            }
        }
    }

    /**
     * This returns the existing MGraph for the log .
     * 
     * @return the MGraph to which the requests are logged
     */
    private MGraph getRequestLogGraph() {
        return tcManager.getMGraph(REQUEST_LOG_GRAPH_NAME);
    }
    
}
