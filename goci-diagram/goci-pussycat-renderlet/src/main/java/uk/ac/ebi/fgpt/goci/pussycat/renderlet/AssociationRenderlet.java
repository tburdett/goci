package uk.ac.ebi.fgpt.goci.pussycat.renderlet;

import net.sourceforge.fluxion.spi.ServiceProvider;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import uk.ac.ebi.fgpt.goci.lang.OntologyConstants;
import uk.ac.ebi.fgpt.goci.pussycat.layout.SVGArea;
import uk.ac.ebi.fgpt.goci.pussycat.layout.SVGCanvas;
import uk.ac.ebi.fgpt.goci.pussycat.renderlet.chromosome.ChromosomeRenderlet;

import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: dwelter
 * Date: 18/04/12
 * Time: 13:38
 * To change this template use File | Settings | File Templates.
 */


@ServiceProvider
public class AssociationRenderlet implements Renderlet<OWLOntology, OWLNamedIndividual> {


    private Logger log = LoggerFactory.getLogger(getClass());
    protected Logger getLog() {
        return log;
    }

    @Override
    public String getName() {

        //this should be name of the trait this is associated with
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public String getDisplayName() {
        return getName();
    }

    @Override
    public String getDescription() {
        return ("This is a renderlet displaying " + getDisplayName());
    }

    @Override
    public boolean canRender(RenderletNexus nexus, Object renderingContext, Object owlEntity) {

        boolean renderable = false;
        if (renderingContext instanceof OWLOntology){
            if (owlEntity instanceof OWLNamedIndividual){
                OWLOntology ontology = (OWLOntology)renderingContext;

                if (owlEntity instanceof OWLNamedIndividual){
                    OWLNamedIndividual individual = (OWLNamedIndividual)owlEntity;

                    if(nexus.getLocationOfRenderedEntity(individual)==null){
                        OWLClassExpression[] allTypes = individual.getTypes(ontology).toArray(new OWLClassExpression[0]);

                        for(int i = 0; i < allTypes.length; i++){
                            OWLClass typeClass = allTypes[i].asOWLClass();

                            if(typeClass.getIRI().equals(IRI.create(OntologyConstants.TRAIT_ASSOCIATION_CLASS_IRI))){
                                renderable = true;
                                break;
                            }
                        }
                    }
                }

                    /*

else{
//means this has already been rendered, e.g. if this is a second query - does this case need to be dealt with?
}*/

            }
        }

        return renderable;

    }

    @Override
    public void render(RenderletNexus nexus, OWLOntology renderingContext, OWLNamedIndividual renderingEntity) {
        System.out.println("Association: " + renderingEntity);

        String bandName = getSNPLocation(renderingEntity, renderingContext);
        Element g;
        boolean bandflag = false;
//there is no other association in this chromosmal band yet - render

        if(nexus.getAssociations(bandName) == null){
            g = nexus.createSVGElement("g");
            String chromosome;

            if(bandName.contains("p")){
                chromosome = bandName.split("p")[0];
            }
            else{
                chromosome = bandName.split("q")[0];
            }

            for(Renderlet r : nexus.getRenderlets()){
                if(r instanceof ChromosomeRenderlet){
                    String comp = r.getName().split(" ")[1];

                    if(comp.equals(chromosome)){
                        g.setAttribute("id",renderingEntity.getIRI().toString());
                        g.setAttribute("transform", chromosomeTransform(chromosome));

                        SVGArea band = ((ChromosomeRenderlet) r).getBands().get(bandName);
    //print statement to keep track of which band is being processed as I've had trouble with some bands
               System.out.println(bandName);
                        if(band != null){

                            double x = band.getX();
                            double y = band.getY();
                            double width = band.getWidth();
                            double  height = band.getHeight();

                            double newY = y+(height/2);
                            double length = 1.75*width;

                            StringBuilder d = new StringBuilder();
                            d.append("m ");
                            d.append(Double.toString(x));
                            d.append(",");
                            d.append(Double.toString(newY));
                            d.append(" ");
                            d.append(Double.toString(length));
                            d.append(",0.0");

                            Element path = nexus.createSVGElement("path");
                            path.setAttribute("d",d.toString());
                            path.setAttribute("style","fill:none;stroke:#211c1d;stroke-width:1.1;stroke-linecap:butt;stroke-linejoin:miter;stroke-miterlimit:4;stroke-opacity:1;stroke-dasharray:none");

                            g.appendChild(path);

                            SVGArea currentArea = new SVGArea(x,newY,length,0,0);
                            RenderingEvent event = new RenderingEvent(renderingEntity, g, currentArea, this);
                            nexus.renderingEventOccurred(event);
                            nexus.setAssociation(bandName, renderingEntity);
                            nexus.addSVGElement(g);
                            break;

                        }
                        else{
                            log.error(bandName + " is not a known cytogenetic band");
                            bandflag = true;
                        }
                    }
                }
            }
        }

//there is already another association in this band - can't render the association but need to render the trait as well as add to various nexus lists
        else{
            System.out.println("Secondary association: " + renderingEntity + " for band " + bandName);
//get the SVG for the first assocation rendered for this band and reuse it for this association, but without adding it to the SVG file
            OWLNamedIndividual previousEntity = (OWLNamedIndividual)nexus.getAssociations(bandName).get(0);
            g = nexus.getRenderingEvent(previousEntity).getRenderedSVG();
            g.setAttribute("id",renderingEntity.getIRI().toString());
            RenderingEvent event = new RenderingEvent(renderingEntity, g, nexus.getLocationOfRenderedEntity(previousEntity),this);
            nexus.renderingEventOccurred(event);
            nexus.setAssociation(bandName,renderingEntity);
        }

        OWLNamedIndividual trait = getTrait(renderingEntity, renderingContext, nexus);

        System.out.println("Trait: " + trait);

        if(!bandflag){
            for (Renderlet r : nexus.getRenderlets()){
                if (r.canRender(nexus, renderingContext, trait)) {
                    getLog().debug("Dispatching render() request to renderlet '" + r.getName() + "'");
                    r.render(nexus, renderingContext, trait);
                }

            }
        }
    }


    public OWLNamedIndividual getTrait(OWLNamedIndividual individual, OWLOntology ontology, RenderletNexus nexus){
        OWLReasoner reasoner = nexus.getReasoner();
        OWLNamedIndividual trait = null;
        String name = "GWAS trait";

        OWLDataFactory dataFactory = OWLManager.createOWLOntologyManager().getOWLDataFactory();

        OWLObjectProperty is_about = dataFactory.getOWLObjectProperty(IRI.create(OntologyConstants.IS_ABOUT_IRI));
        OWLIndividual[] related = individual.getObjectPropertyValues(is_about,ontology).toArray(new OWLIndividual[0]);
        OWLClass ef = dataFactory.getOWLClass(IRI.create(OntologyConstants.EXPERIMENTAL_FACTOR_CLASS_IRI));

        Set<OWLNamedIndividual> allEFs = reasoner.getInstances(ef,false).getFlattened();

        for(int i = 0; i < related.length; i++){
            if(allEFs.contains(related[i])){
                trait = (OWLNamedIndividual)related[i];
            }
        }

        return trait;
    }

    public String getSNPLocation(OWLNamedIndividual individual, OWLOntology ontology){
        String bandName = null;
        OWLDataFactory dataFactory = OWLManager.createOWLOntologyManager().getOWLDataFactory();

//get all the is_about individuals of this trait-assocation
        OWLObjectProperty is_about = dataFactory.getOWLObjectProperty(IRI.create(OntologyConstants.IS_ABOUT_IRI));
        OWLIndividual[] related = individual.getObjectPropertyValues(is_about,ontology).toArray(new OWLIndividual[0]);

        for(int k = 0; k < related.length; k++){
            OWLClassExpression[] allTypes = related[k].getTypes(ontology).toArray(new OWLClassExpression[0]);

//find the individual that is of type SNP
            for(int i = 0; i < allTypes.length; i++){
                OWLClass typeClass = allTypes[i].asOWLClass();

                if(typeClass.getIRI().equals(IRI.create(OntologyConstants.SNP_CLASS_IRI))){
                    OWLNamedIndividual SNP = (OWLNamedIndividual)related[k];

//get the SNP's cytogenetic band
                    OWLObjectProperty has_band = dataFactory.getOWLObjectProperty(IRI.create(OntologyConstants.LOCATED_IN_PROPERTY_IRI));
                    OWLIndividual band = SNP.getObjectPropertyValues(has_band,ontology).toArray(new OWLIndividual[0])[0];

//get the band's name
                    OWLDataProperty has_name = dataFactory.getOWLDataProperty(IRI.create(OntologyConstants.HAS_NAME_PROPERTY_IRI));
                    bandName = band.getDataPropertyValues(has_name,ontology).toArray(new OWLLiteral[0])[0].getLiteral();

                }
            }
        }
        return bandName;
    }

    public String chromosomeTransform(String chromosome){
        int position;
        if(chromosome.equals("X")){
            position = 22;
        }
        else if(chromosome.equals("Y")){
            position = 23;
        }
        else {
            position = Integer.parseInt(chromosome)-1;
        }
        int height = SVGCanvas.canvasHeight;
        int width = SVGCanvas.canvasWidth;

        double chromWidth = (double)width/12;
        double xCoordinate;
        double yCoordinate = 0;

        if (position < 12){
            xCoordinate = position * chromWidth;
        }
        else{
            xCoordinate = (position-12) * chromWidth;
            yCoordinate = (double)height/2;
        }

        StringBuilder builder = new StringBuilder();
        builder.append("translate(");
        builder.append(Double.toString(xCoordinate));
        builder.append(",");
        builder.append(Double.toString(yCoordinate));
        builder.append(")");

        return builder.toString();
    }
    

}