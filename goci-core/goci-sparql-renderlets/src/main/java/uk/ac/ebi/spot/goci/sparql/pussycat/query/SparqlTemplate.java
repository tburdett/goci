package uk.ac.ebi.spot.goci.sparql.pussycat.query;

import com.hp.hpl.jena.graph.Graph;
import com.hp.hpl.jena.query.ParameterizedSparqlString;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.QuerySolutionMap;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.query.Syntax;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.impl.ResourceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.stereotype.Service;
import uk.ac.ebi.fgpt.lode.exception.LodeException;
import uk.ac.ebi.fgpt.lode.service.JenaQueryExecutionService;
import uk.ac.ebi.spot.goci.pussycat.exception.DataIntegrityViolationException;
import uk.ac.ebi.spot.goci.sparql.exception.SparqlQueryException;

import javax.annotation.Resource;
import java.net.URI;
import java.util.*;

/**
 * Javadocs go here!
 *
 * @author Tony Burdett
 * @date 21/08/14
 */
@Service
public class SparqlTemplate {

    private JenaQueryExecutionService jenaQueryExecutionService;

    private String prefixes;

    @Resource(name = "prefixProperties")
    private Properties prefixProperties;

    private Map<String, Boolean> askCache = new TreeMap<>();
    private Map<String, String> labelCache = new TreeMap<>();
    private Map<String, URI> typeCache = new TreeMap<>();

    public JenaQueryExecutionService getJenaQueryExecutionService() {
        return jenaQueryExecutionService;
    }

    @Autowired
    @Required
    public void setJenaQueryExecutionService(JenaQueryExecutionService jenaQueryExecutionService) {
        this.jenaQueryExecutionService = jenaQueryExecutionService;
    }

    private Logger log = LoggerFactory.getLogger(getClass());

    protected Logger getLog() {
        return log;
    }

    public String getPrefixes() {
        return prefixes;
    }

    public void setPrefixes(String prefixes) {
        this.prefixes = prefixes;
    }

    public Properties getPrefixProperties() {
        return prefixProperties;
    }

    //    @Autowired
    //    @Required
    //    public void setPrefixProperties(Properties prefixProperties) {
    //        this.prefixProperties = prefixProperties;
    //    }

    public String getPrefixString() {
        if (getPrefixes() != null) {
            return getPrefixes();
        }
        else if (getPrefixProperties() != null) {
            StringBuilder sb = new StringBuilder();
            for (String prefix : getPrefixProperties().stringPropertyNames()) {
                sb.append("PREFIX ")
                        .append(prefix)
                        .append(":<")
                        .append(getPrefixProperties().get(prefix))
                        .append(">\n");
            }
            return sb.toString();
        }
        else {
            return "";
        }
    }

    public boolean ask(URI instance, URI type) {
        String sparql = "ASK {<" + instance.toString() + "> a <" + type.toString() + ">}";
        return ask(sparql);
    }

    public boolean ask(String sparql) {
        sparql = getPrefixString().concat(sparql);
        if(askCache.containsKey(sparql)){
            return askCache.get(sparql);
        }
        Graph g = getJenaQueryExecutionService().getDefaultGraph();
        Query q1 = QueryFactory.create(sparql, Syntax.syntaxARQ);
        QueryExecution execute = null;
        try {
            execute = getJenaQueryExecutionService().getQueryExecution(g, q1, false);
            boolean result = execute.execAsk();
            askCache.put(sparql, result);
            return result;
        }
        catch (LodeException e) {
            throw new SparqlQueryException("Failed to execute ask '" + sparql + "'", e);
        }
        finally {
            if (execute != null) {
                execute.close();
                if (g != null) {
                    g.close();
                }
            }
        }
    }

    public List<URI> query(String sparql, String uriFieldName) {
        return query(sparql, new URIMapper(uriFieldName));
    }

    public <T> List<T> query(String sparql, QuerySolutionMapper<T> qsm) {
        return query(sparql, new QuerySolutionResultSetMapper<T>(qsm));
    }

    public <T> T query(String sparql, ResultSetMapper<T> rsm) {
        sparql = getPrefixString().concat(sparql);
        Graph g = getJenaQueryExecutionService().getDefaultGraph();
        Query q1 = QueryFactory.create(sparql, Syntax.syntaxARQ);
        QueryExecution execute = null;
        try {
            execute = getJenaQueryExecutionService().getQueryExecution(g, q1, false);
            ResultSet results = execute.execSelect();
            return rsm.mapResultSet(results);
        }
        catch (LodeException e) {
            throw new SparqlQueryException("Failed to execute query '" + sparql + "'", e);
        }
        finally {
            if (execute != null) {
                execute.close();
                if (g != null) {
                    g.close();
                }
            }
        }
    }

    public List<URI> query(String sparql, String uriFieldName, Object... args) {
        return query(sparql, new URIMapper(uriFieldName), args);
    }

    public <T> List<T> query(String sparql, QuerySolutionMapper<T> qsm, Object... args) {
        return query(sparql, new QuerySolutionResultSetMapper<T>(qsm), args);
    }

    public <T> T query(String sparql, ResultSetMapper<T> rsm, Object... args) {
        sparql = getPrefixString().concat(sparql);
        Graph g = getJenaQueryExecutionService().getDefaultGraph();

        Map<String, Object> bindingMap = new HashMap<String, Object>();
        int i = 0;
        for (Object o : args) {
            String argName = "?_arg" + i++;
            sparql = sparql.replaceFirst("\\?\\?", argName);
            bindingMap.put(argName, o);
        }
        Query q1 = QueryFactory.create(sparql, Syntax.syntaxARQ);

        QuerySolutionMap initialBinding = new QuerySolutionMap();
        for (String argName : bindingMap.keySet()) {
            Object argValue = bindingMap.get(argName);
            RDFNode arg;
            if (argValue instanceof URI) {
                arg = new ResourceImpl(argValue.toString());
            }
            else {
                arg = getLiteralNode(argValue);
            }
            initialBinding.add(argName, arg);
        }
        ParameterizedSparqlString queryString = new ParameterizedSparqlString(q1.toString(), initialBinding);

        QueryExecution execute = null;
        try {
            execute = getJenaQueryExecutionService().getQueryExecution(g, queryString.asQuery(), false);
            getLog().debug(q1.toString());
            ResultSet results = execute.execSelect();
            return rsm.mapResultSet(results);
        }
        catch (LodeException e) {
            throw new SparqlQueryException("Failed to execute query '" + sparql + "'", e);
        }
        finally {
            if (execute != null) {
                execute.close();
                if (g != null) {
                    g.close();
                }
            }
        }
    }

    public List<URI> list(URI type) {
        return query("SELECT DISTINCT ?uri WHERE { ?uri a <" + type.toString() + "> . FILTER (!isBlank(?uri)) }",
                     new QuerySolutionMapper<URI>() {
                         @Override public URI mapQuerySolution(QuerySolution qs) {
                             return URI.create(qs.getResource("uri").getURI());
                         }
                     });
    }

    public String label(final URI entity) {
        String sparql = getPrefixString().concat(
                "SELECT DISTINCT ?label WHERE { <" + entity.toString() + "> rdfs:label ?label . }");
        if(labelCache.containsKey(sparql)){
            return labelCache.get(sparql);
        }
        String result = query(sparql, new ResultSetMapper<String>() {
            @Override
            public String mapResultSet(ResultSet rs) {
                String result = null;
                while (rs.hasNext()) {
                    if (result != null) {
                        throw new SparqlQueryException(new DataIntegrityViolationException(
                                "More than one rdfs:label for' " + entity.toString() + "'"));
                    }
                    QuerySolution qs = rs.next();
                    result = qs.getLiteral("label").getLexicalForm();
                }
                if (result == null) {
                    result = "Annotation tbc";
                }
                return result;
            }
        });
        labelCache.put(sparql, result);
        return result;
    }

    public URI type(final URI entity) {
        String sparql =
                getPrefixString().concat("SELECT DISTINCT ?type WHERE { <" + entity.toString() + "> rdf:type ?type . " +
                                                 "FILTER ( ?type != owl:Class ) . " +
                                                 "FILTER ( ?type != owl:NamedIndividual ) }");
        if(typeCache.containsKey(sparql)){
            return typeCache.get(sparql);
        }
        URI result = query(sparql, new ResultSetMapper<URI>() {
            @Override
            public URI mapResultSet(ResultSet rs) {
                URI result = null;
                while (rs.hasNext()) {
                    if (result != null) {
                        throw new SparqlQueryException(new DataIntegrityViolationException(
                                "More than one non-owl rdf:type for' " + entity.toString() + "'"));
                    }
                    QuerySolution qs = rs.next();
                    result = URI.create(qs.getResource("type").getURI());
                }
                return result;
            }
        });
        typeCache.put(sparql, result);
        return result;
    }

    public List<URI> types(final URI entity) {
        String sparql =
                getPrefixString().concat("SELECT DISTINCT ?type " +
                                                 "WHERE { " +
                                                 "<" + entity.toString() + "> rdf:type ?target . " +
                                                 "?target rdfs:subClassOf* ?type . " +
                                                 "FILTER ( !isBlank(?type) ) . " +
                                                 "FILTER ( ?type != owl:Class ) . " +
                                                 "FILTER ( ?type != owl:NamedIndividual ) . }");
        return query(sparql,
                     new QuerySolutionMapper<URI>() {
                         @Override public URI mapQuerySolution(QuerySolution qs) {
                             return URI.create(qs.getResource("type").getURI());
                         }
                     });
    }

    protected RDFNode getLiteralNode(Object o) {
        Model m = ModelFactory.createDefaultModel();
        return m.createTypedLiteral(o);
    }

}
