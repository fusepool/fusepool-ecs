package eu.fusepool.ecs.core;

import eu.fusepool.ecs.ontologies.ECS;
import java.security.AccessController;
import java.security.AllPermission;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import org.apache.clerezza.jaxrs.utils.TrailingSlash;
import org.apache.clerezza.platform.content.DiscobitsHandler;
import org.apache.clerezza.platform.cris.IndexService;
import org.apache.clerezza.platform.graphnodeprovider.GraphNodeProvider;
import org.apache.clerezza.platform.graphprovider.content.ContentGraphProvider;
import org.apache.clerezza.rdf.core.BNode;
import org.apache.clerezza.rdf.core.Language;
import org.apache.clerezza.rdf.core.Literal;
import org.apache.clerezza.rdf.core.LiteralFactory;
import org.apache.clerezza.rdf.core.MGraph;
import org.apache.clerezza.rdf.core.NonLiteral;
import org.apache.clerezza.rdf.core.PlainLiteral;
import org.apache.clerezza.rdf.core.Resource;
import org.apache.clerezza.rdf.core.Triple;
import org.apache.clerezza.rdf.core.UriRef;
import org.apache.clerezza.rdf.core.access.TcManager;
import org.apache.clerezza.rdf.core.impl.PlainLiteralImpl;
import org.apache.clerezza.rdf.core.impl.SimpleMGraph;
import org.apache.clerezza.rdf.core.impl.TripleImpl;
import org.apache.clerezza.rdf.cris.Condition;
import org.apache.clerezza.rdf.cris.CountFacetCollector;
import org.apache.clerezza.rdf.cris.FacetCollector;
import org.apache.clerezza.rdf.cris.PathVirtualProperty;
import org.apache.clerezza.rdf.cris.PropertyHolder;
import org.apache.clerezza.rdf.cris.VirtualProperty;
import org.apache.clerezza.rdf.cris.WildcardCondition;
import org.apache.clerezza.rdf.ontologies.DC;
import org.apache.clerezza.rdf.ontologies.DCTERMS;
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
import org.apache.stanbol.commons.security.UserUtil;
import org.apache.stanbol.commons.web.viewable.RdfViewable;
import org.apache.stanbol.entityhub.model.clerezza.RdfValueFactory;
import org.apache.stanbol.entityhub.servicesapi.model.Entity;
import org.apache.stanbol.entityhub.servicesapi.model.Representation;
import org.apache.stanbol.entityhub.servicesapi.site.SiteManager;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Uses the SiteManager to resolve entities. Every requested is recorded to a
 * graph. The client gets information and meta-information about the resource
 * and sees all previous requests for that resource.
 */
@Component(immediate=true)
@Service({Object.class, ContentStore.class})
@Property(name = "javax.ws.rs", boolValue = true)
@Path("ecs")
public class ContentStoreImpl implements ContentStore {

    /**
     * Using slf4j for normal logging
     */
    private static final Logger log = LoggerFactory.getLogger(ContentStoreImpl.class);
    public static final int PREVIEW_LENGTH = 200;
    final static UriRef MEDIA_TITLE = new UriRef("http://www.w3.org/ns/ma-ont#title");

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
    @Reference
    private ContentGraphProvider contentGraphProvider;
    /**
     * This service allows to get entities from configures sites
     */
    @Reference
    private SiteManager siteManager;
    private LiteralFactory literalFactory = LiteralFactory.getInstance();
    private final static String CONTENT_PREFIX = "content/";
    //private int MAX_FACETS = 10;

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
        indexProperties.add(new PropertyHolder(RDF.type));
        indexService.addDefinitionVirtual(ECS.ContentItem, indexProperties);
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
    public RdfViewable serviceEntryPriviledged(@Context final UriInfo uriInfo,
            @QueryParam("subject") final List<UriRef> subjects,
            @QueryParam("search") final List<String> searchs,
            @QueryParam("items") final Integer items,
            @QueryParam("offset") final @DefaultValue("0") Integer offset,
            @QueryParam("maxFacets") final @DefaultValue("10") Integer maxFacets) throws Exception {
        //here we can still access the user name
        final String userName = UserUtil.getCurrentUserName();
        try {
            return AccessController.doPrivileged(new PrivilegedExceptionAction<RdfViewable>() {
                public RdfViewable run() throws Exception {
                    return serviceEntry(uriInfo, subjects, searchs, items, offset, maxFacets);
                }
            });
        } catch (PrivilegedActionException e) {
            throw e.getException();
        }

    }

    public RdfViewable serviceEntry(@Context final UriInfo uriInfo,
            @QueryParam("subject") final List<UriRef> subjects,
            @QueryParam("search") final List<String> searchs,
            @QueryParam("items") Integer items,
            @QueryParam("offset") @DefaultValue("0") Integer offset,
            @QueryParam("maxFacets") @DefaultValue("10") Integer maxFacets) throws Exception {
        //this maks sure we are nt invoked with a trailing slash which would affect
        //relative resolution of links (e.g. css)
        TrailingSlash.enforcePresent(uriInfo);
        String viewUriString = uriInfo.getRequestUri().toString();//getAbsolutePath().toString();
        if (items == null) {
            if (viewUriString.indexOf('?') > 0) {
                viewUriString += "&";
            } else {
                viewUriString += "?";
            }
            viewUriString += "items=10";
            items = 10;
        }
        final UriRef contentStoreViewUri = new UriRef(viewUriString);
        //This is the URI without query params
        final UriRef contentStoreUri = new UriRef(uriInfo.getAbsolutePath().toString());
        GraphNode node = getContentStoreView(contentStoreUri, contentStoreViewUri,
                subjects, searchs, items,
                offset, maxFacets, false);
        //What we return is the GraphNode we created with a template path
        return new RdfViewable("ContentStoreView", node, ContentStoreImpl.class);
    }

    /**
     * Returns a GraphNode of type ContentStoreView.
     *
     * @param contentStoreUri The IRI of the content store to use
     * @param contentStoreViewUri The IRI that shall be assigned to the returned
     * view
     * @param subjects The dc:subjects the matching content items shall have
     * @param searchs The search patterns the matching documents shall satisfy
     * @param items the number of items to return
     * @param offset the position at which to start items (for pagination)
     * @param maxFacets the maximum number of facets the result shall contain
     * @return a GraphNode describing the ContentStoreView
     */
    @Override
    public GraphNode getContentStoreView(final UriRef contentStoreUri,
            final UriRef contentStoreViewUri,
            final Collection<UriRef> subjects,
            final Collection<String> searchs,
            Integer items,
            Integer offset,
            Integer maxFacets,
            boolean withContent) {
        return getContentStoreView(contentStoreUri, contentStoreViewUri, 
                subjects, Collections.EMPTY_LIST, searchs, 
                items, offset, maxFacets, withContent);
    }
    
    @Override
    public GraphNode getContentStoreView(final UriRef contentStoreUri,
            final UriRef contentStoreViewUri,
            final Collection<UriRef> subjects,
            final Collection<UriRef> types,
            final Collection<String> searchs,
            Integer items,
            Integer offset,
            Integer maxFacets,
            boolean withContent) {
        //the in memory graph to which the triples for the response are added
        final MGraph resultGraph = new IndexedMGraph();
        //This GraphNode represents the service within our result graph
        final GraphNode node = new GraphNode(contentStoreViewUri, resultGraph);
        node.addProperty(RDF.type, ECS.ContentStoreView);
        node.addProperty(ECS.store, contentStoreUri);
        node.addProperty(RDFS.comment, new PlainLiteralImpl("An enhanced content store"));
        final List<Condition> conditions = new ArrayList<Condition>();
        for (UriRef subject : subjects) {
            addResourceDescription(subject, resultGraph);
            node.addProperty(ECS.subject, subject);
            conditions.add(new WildcardCondition(new PropertyHolder(DC.subject), subject.getUnicodeString()));
        }
        for (UriRef type : types) {
            addResourceDescription(type, resultGraph);
            node.addProperty(ECS.type, type);
            conditions.add(new WildcardCondition(new PropertyHolder(RDF.type), type.getUnicodeString()));
        }
        for (String search : searchs) {
            node.addPropertyValue(ECS.search, search);
            conditions.add(new WildcardCondition(new PropertyHolder(SIOC.content), "*" + search.toLowerCase() + "*"));
        }
        if (conditions.isEmpty()) {
            conditions.add(new WildcardCondition(new PropertyHolder(SIOC.content), "*"));
        }
        final Set<VirtualProperty> facetProperties = new HashSet<VirtualProperty>();
        facetProperties.add((VirtualProperty) new PropertyHolder(DC.subject));
        facetProperties.add((VirtualProperty) new PropertyHolder(RDF.type));
        
        final FacetCollector facetCollector = new CountFacetCollector(
                facetProperties);
        //log.info("starting find");
        final List<NonLiteral> matchingNodes = indexService.findResources(conditions, facetCollector);
        //log.info("completed find");
        node.addPropertyValue(ECS.contentsCount, matchingNodes.size());
        {
            //facets
            final Set<Map.Entry<String, Integer>> facets = facetCollector.getFacets(new PropertyHolder(DC.subject));
            final List<Map.Entry<String, Integer>> faceList = new ArrayList<Map.Entry<String, Integer>>(facets);
            Collections.sort(faceList, new Comparator<Entry<String, Integer>>() {
                public int compare(Entry<String, Integer> o1, Entry<String, Integer> o2) {
                    return o2.getValue().compareTo(o1.getValue());
                }
            });
            for (int i = 0; i < Math.min(maxFacets, faceList.size()); i++) {
                Entry<String, Integer> entry = faceList.get(i);
                final BNode facetResource = new BNode();
                final GraphNode facetNode = new GraphNode(facetResource, resultGraph);
                node.addProperty(ECS.facet, facetResource);
                final UriRef facetValue = new UriRef(entry.getKey());
                final Integer facetCount = entry.getValue();
                facetNode.addProperty(ECS.facetValue, facetValue);
                addResourceDescription(facetValue, resultGraph);
                facetNode.addPropertyValue(ECS.facetCount, facetCount);
            }
        }
        {
            //TODO remove code duplication
            //type-facets
            //log.info("adding type facets");
            final Set<Map.Entry<String, Integer>> facets = facetCollector.getFacets(new PropertyHolder(RDF.type));
            final List<Map.Entry<String, Integer>> faceList = new ArrayList<Map.Entry<String, Integer>>(facets);
            Collections.sort(faceList, new Comparator<Entry<String, Integer>>() {
                public int compare(Entry<String, Integer> o1, Entry<String, Integer> o2) {
                    return o2.getValue().compareTo(o1.getValue());
                }
            });
            int adaptedMaxFacets = maxFacets;
            for (int i = 0; i < Math.min(adaptedMaxFacets, faceList.size()); i++) {
                Entry<String, Integer> entry = faceList.get(i);
                final BNode facetResource = new BNode();
                final GraphNode facetNode = new GraphNode(facetResource, resultGraph);
                final UriRef facetValue = new UriRef(entry.getKey());
                if (facetValue.equals(ECS.ContentItem) || facetValue.equals(DISCOBITS.InfoDiscoBit)) {
                    adaptedMaxFacets++;
                    continue;
                }
                node.addProperty(ECS.typeFacet, facetResource);
                final Integer facetCount = entry.getValue();
                facetNode.addProperty(ECS.facetValue, facetValue);
                addResourceDescription(facetValue, resultGraph);
                facetNode.addPropertyValue(ECS.facetCount, facetCount);
            }
            //log.info("added type facets");
        }
        final NonLiteral matchingContentsList = new BNode();
        if (matchingNodes.size() > 0) {
            node.addProperty(ECS.contents, matchingContentsList);
            final RdfList matchingContents = new RdfList(matchingContentsList, resultGraph);
            matchingContents.addAll(matchingNodes.subList(
                    Math.min(offset, matchingNodes.size()),
                    Math.min(offset + items, matchingNodes.size())));
            for (Resource content : matchingContents) {
                GraphNode cgContent = graphNodeProvider.getLocal((UriRef) content);
                addRelevantDescription(cgContent, resultGraph, withContent);

            }
        }
        //log.info("returning node");
        return node;
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

    //an alternative to retrieveing via entityhub
    @GET
    @Path("entity")
    public RdfViewable getEntity(@QueryParam("uri") UriRef entityUri) {
        final MGraph resultMGraph = new SimpleMGraph();
        addResourceDescription(entityUri, resultMGraph);
        final GraphNode resultNode = new GraphNode(entityUri, resultMGraph);
        resultNode.addPropertyValue(RDFS.comment, "here you go");
        //TODO use own rendering spec
        return new RdfViewable("ContentStoreView", resultNode, ContentStoreImpl.class);

    }

    /*   @GET
     @Path("test")
     public TripleCollection test() {
     return tcManager.getMGraph(Constants.CONTENT_GRAPH_URI);
     } */
    @GET
    @Path("reindex")
    public String reIndex() {
        AccessController.checkPermission(new AllPermission());
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
        Response.ResponseBuilder responseBuilder =
                Response.ok(data, mediaType)
                .header("Link", "<" + resourcePath + ".meta>; rel=meta");
        return responseBuilder.build();
    }

    @GET
    @Path(CONTENT_PREFIX + "{hash: .*}.meta")
    public RdfViewable getMeta(@Context final UriInfo uriInfo) {
        final String resourcePath = uriInfo.getAbsolutePath().toString();
        final UriRef contentUri = new UriRef(resourcePath.substring(0, resourcePath.length() - 5));
        return getMeta(contentUri);
    }

    @GET
    @Path("meta")
    public RdfViewable getMeta(@QueryParam("iri") final UriRef contentUri) {
        final GraphNode nodeWithoutEnhancements = graphNodeProvider.getLocal(contentUri);
        return new RdfViewable("Meta", nodeWithoutEnhancements, ContentStoreImpl.class);
        /*final MGraph enhancementsGraph = tcManager.getMGraph(StanbolEnhancerMetadataGenerator.ENHANCEMENTS_GRAPH);
        return new RdfViewable("Meta", new GraphNode(contentUri,
                new UnionMGraph(nodeWithoutEnhancements.getGraph(), enhancementsGraph)), ContentStoreImpl.class);*/
    }

    /**
     * Add the description of a serviceUri to the specified MGraph using
     * SiteManager. The description includes the metadata provided by the
     * SiteManager.
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
        }
        //Also add selected properties from content graph
        //Note that we have to be selective or we would add all the documents the
        //entity is a subject of.
        MGraph cg = contentGraphProvider.getContentGraph();
        Iterator<Triple> allOutgoing = cg.filter(iri, null, null);
        while (allOutgoing.hasNext()) {
            Triple t = allOutgoing.next();
            if (t.getPredicate().equals(RDF.type)) {
                mGraph.add(t);
                continue;
            }
            if (t.getObject() instanceof Literal) {
                mGraph.add(t);
            }
        }
    }

    private void addRelevantDescription(GraphNode cgContent, MGraph resultGraph, boolean withContent) {
        Iterator<Literal> valueIter = cgContent.getLiterals(SIOC.content);
        //if (!withContent) {
            while (valueIter.hasNext()) {
                final Literal valueLit = valueIter.next();
                final String textualContent = valueLit.getLexicalForm();
                final String preview = textualContent.substring(
                        0, Math.min(PREVIEW_LENGTH, textualContent.length()))
                        .replace('\n', ' ')
                        .replace("\r", "");
                Language language = null;
                if (valueLit instanceof PlainLiteral) {
                    language = ((PlainLiteral) valueLit).getLanguage();
                }
                resultGraph.add(new TripleImpl((NonLiteral) cgContent.getNode(), ECS.textPreview,
                        new PlainLiteralImpl(preview, language)));
            }
        //}
        copyProperties(cgContent, resultGraph, DCTERMS.title, DCTERMS.abstract_,
                RDFS.comment, DC.description, MEDIA_TITLE);
        if (withContent) {
            copyProperties(cgContent, resultGraph, SIOC.content);
        }
    }

    private void copyProperties(GraphNode fromNode, MGraph toGraph, UriRef... properties) {
        for (UriRef property : properties) {
            Iterator<Resource> objects = fromNode.getObjects(property);
            while (objects.hasNext()) {
                Resource object = objects.next();
                toGraph.add(new TripleImpl((NonLiteral) fromNode.getNode(),
                        property, object));
            }
        }
    }
}
