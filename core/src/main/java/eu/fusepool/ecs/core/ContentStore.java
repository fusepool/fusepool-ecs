package eu.fusepool.ecs.core;

import eu.fusepool.ecs.ontologies.ECS;
import java.security.Permission;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import org.apache.clerezza.jaxrs.utils.TrailingSlash;
import org.apache.clerezza.platform.Constants;
import org.apache.clerezza.platform.content.DiscobitsHandler;
import org.apache.clerezza.platform.cris.IndexService;
import org.apache.clerezza.platform.graphnodeprovider.GraphNodeProvider;
import org.apache.clerezza.rdf.core.BNode;
import org.apache.clerezza.rdf.core.MGraph;
import org.apache.clerezza.rdf.core.NonLiteral;
import org.apache.clerezza.rdf.core.TripleCollection;
import org.apache.clerezza.rdf.core.UriRef;
import org.apache.clerezza.rdf.core.access.EntityAlreadyExistsException;
import org.apache.clerezza.rdf.core.access.TcManager;
import org.apache.clerezza.rdf.core.access.security.TcAccessController;
import org.apache.clerezza.rdf.core.access.security.TcPermission;
import org.apache.clerezza.rdf.core.impl.PlainLiteralImpl;
import org.apache.clerezza.rdf.cris.Condition;
import org.apache.clerezza.rdf.cris.PathVirtualProperty;
import org.apache.clerezza.rdf.cris.PropertyHolder;
import org.apache.clerezza.rdf.cris.VirtualProperty;
import org.apache.clerezza.rdf.cris.WildcardCondition;
import org.apache.clerezza.rdf.ontologies.DC;
import org.apache.clerezza.rdf.ontologies.DISCOBITS;
import org.apache.clerezza.rdf.ontologies.RDF;
import org.apache.clerezza.rdf.ontologies.RDFS;
import org.apache.clerezza.rdf.ontologies.SIOC;
import org.apache.clerezza.rdf.utils.GraphNode;
import org.apache.clerezza.rdf.utils.RdfList;
import org.apache.clerezza.rdf.utils.UnionMGraph;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.stanbol.commons.indexedgraph.IndexedMGraph;
import org.apache.stanbol.commons.web.viewable.RdfViewable;
import org.apache.stanbol.enhancer.servicesapi.ChainManager;
import org.apache.stanbol.enhancer.servicesapi.EnhancementJobManager;
import org.apache.stanbol.entityhub.model.clerezza.RdfValueFactory;
import org.apache.stanbol.entityhub.servicesapi.model.Entity;
import org.apache.stanbol.entityhub.servicesapi.model.Representation;
import org.apache.stanbol.entityhub.servicesapi.site.SiteManager;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wymiwyg.commons.util.MD5;

/**
 * Uses the SiteManager to resolve entities. Every requested is recorded to a
 * graph. The client gets information and meta-information about the resource
 * and sees all previous requests for that resource.
 */
@Component
@Service(Object.class)
@Property(name = "javax.ws.rs", boolValue = true)
@Path("ecs")
public class ContentStore {

    /**
     * Using slf4j for normal logging
     */
    private static final Logger log = LoggerFactory.getLogger(ContentStore.class);
    /**
     * This service allows accessing and creating persistent triple collections
     */
    @Reference
    private TcManager tcManager;
    @Reference
    private DiscobitsHandler discobitsHandler;
    @Reference
    private IndexService indexService;
    @Reference
    private GraphNodeProvider graphNodeProvider;
    private final static String CONTENT_PREFIX = "content/";

    @Activate
    protected void activate(ComponentContext context) {
        log.info("Enhanced Content Store being activated");
        final List<VirtualProperty> indexProperties = new ArrayList<VirtualProperty>();
        indexProperties.add(new PropertyHolder(SIOC.content));
        final List<UriRef> subjectLabelPath = new ArrayList<UriRef>();
        subjectLabelPath.add(DC.subject);
        subjectLabelPath.add(RDFS.label);
        final VirtualProperty subjectLabel = new PathVirtualProperty(subjectLabelPath);
        indexProperties.add(subjectLabel);
        indexProperties.add(new PropertyHolder(DC.subject));
        indexService.addDefinitionVirtual(DISCOBITS.InfoDiscoBit, indexProperties);
    }

    @Deactivate
    protected void deactivate(ComponentContext context) {
        log.info("The Enhanced COntent Store");
    }

    /**
     * This method return an RdfViewable, this is an RDF serviceUri with
     * associated presentational information.
     */
    @GET
    //temporarily restricting till there is a templates
    @Produces("application/rdf+xml")
    public RdfViewable serviceEntry(@Context final UriInfo uriInfo,
            @QueryParam("subject") final List<UriRef> subjects,
            @QueryParam("search") final List<String> searchs) throws Exception {
        //this maks sure we are nt invoked with a trailing slash which would affect
        //relative resolution of links (e.g. css)
        TrailingSlash.enforcePresent(uriInfo);
        final String resourcePath = uriInfo.getAbsolutePath().toString();
        //The URI at which this service was accessed accessed, this will be the 
        //central serviceUri in the response
        final UriRef serviceUri = new UriRef(resourcePath);
        //the in memory graph to which the triples for the response are added
        final MGraph resultGraph = new IndexedMGraph();
        //This GraphNode represents the service within our result graph
        final GraphNode node = new GraphNode(serviceUri, resultGraph);
        //The triples will be added to the first graph of the union
        //i.e. to the in-memory responseGraph
        //TODO the uri if the view has always params the one of the store is the one without params
        node.addProperty(RDF.type, ECS.ContentStoreView);
        node.addProperty(RDFS.comment, new PlainLiteralImpl("An enhanced content store"));
        final List<Condition> conditions = new ArrayList<Condition>();
        for (UriRef subject : subjects) {
            node.addProperty(DC.subject, subject);
            conditions.add(new WildcardCondition(new PropertyHolder(DC.subject), subject.getUnicodeString()));
        }
        for (String search : searchs) {
            node.addPropertyValue(ECS.search, search);
            conditions.add(new WildcardCondition(new PropertyHolder(SIOC.content), "*" + search + "*"));
        }
        if (conditions.isEmpty()) {
            conditions.add(new WildcardCondition(new PropertyHolder(SIOC.content), "*"));
        }
        final List<NonLiteral> matchingNodes = indexService.findResources(conditions);
        final NonLiteral matchingContentsList = new BNode();
        if (matchingNodes.size() > 0) {
            node.addProperty(ECS.contents, matchingContentsList);
            final RdfList matchingContents = new RdfList(matchingContentsList, resultGraph);
            matchingContents.addAll(matchingNodes);
        }
        //matchingContents.add(new UriRef("http://example/"));
        //What we return is the GraphNode we created with a template path
        return new RdfViewable("ResourceResolver", node, ContentStore.class);
    }

    @POST
    public String postContent(@Context final UriInfo uriInfo, final byte[] data,
            @HeaderParam("Content-Type") MediaType contentType) {
        final String digest = DigestUtils.md5Hex(data);
        String resourcePath = uriInfo.getAbsolutePath().toString();
        if (!resourcePath.endsWith("/")) {
            resourcePath += '/';
        }
        resourcePath += CONTENT_PREFIX;
        final UriRef contentUri = new UriRef(resourcePath + digest);
        discobitsHandler.put(contentUri, contentType, data);
        return "Posted " + data.length + " bytes, with uri " + contentUri + ": " + contentType;
    }

    @GET
    @Path("test")
    public TripleCollection test() {
        return tcManager.getMGraph(Constants.CONTENT_GRAPH_URI);
    }

    @GET
    @Path("reindex")
    public String reIndex() {
        indexService.reCreateIndex();
        return "re-indexed";
    }
    
    @GET
    @Path(CONTENT_PREFIX + "{hash: .*}")
    public Response getContent(@Context final UriInfo uriInfo) {
        final String resourcePath = uriInfo.getAbsolutePath().toString();
        final UriRef contentUri = new UriRef(resourcePath);
        final byte[] data = discobitsHandler.getData(contentUri);
        final MediaType mediaType = discobitsHandler.getMediaType(contentUri);
        Response.ResponseBuilder responseBuilder = Response.ok(data, mediaType);
        return responseBuilder.build();
    }

    @GET
    @Produces({"application/rdf+xml"})
    @Path(CONTENT_PREFIX + "{hash: .*}.meta")
    public RdfViewable getMeta(@Context final UriInfo uriInfo) {
        final String resourcePath = uriInfo.getAbsolutePath().toString();
        final UriRef contentUri = new UriRef(resourcePath.substring(0, resourcePath.length() - 5));
        return new RdfViewable("ResourceResolver", graphNodeProvider.getLocal(contentUri), ContentStore.class);
    }
}
