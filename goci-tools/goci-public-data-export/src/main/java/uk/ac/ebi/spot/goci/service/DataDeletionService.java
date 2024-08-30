package uk.ac.ebi.spot.goci.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.ebi.spot.goci.model.Ancestry;
import uk.ac.ebi.spot.goci.model.Association;
import uk.ac.ebi.spot.goci.model.Publication;
import uk.ac.ebi.spot.goci.model.Study;
import uk.ac.ebi.spot.goci.repository.AncestryRepository;

import java.util.Collection;
import java.util.List;

/**
 * Created by dwelter on 09/01/17.
 */

@Service
public class DataDeletionService {

    private final Logger log = LoggerFactory.getLogger(getClass());

    protected Logger getLog() {
        return log;
    }

    private StudyService studyService;
    private AncestryRepository ancestryRepository;
    private LociAttributesService lociAttributesService;
    private AssociationService associationService;
    private PublicationAuthorsService publicationAuthorsService;
    private PublicationService publicationService;

    @Autowired
    public DataDeletionService(StudyService studyService,
                               AncestryRepository ancestryRepository,
                               CuratorTrackingService curatorTrackingService,
                               WeeklyTrackingService weeklyTrackingService,
                               LociAttributesService lociAttributesService,
                               AssociationService associationService,
                               PublicationAuthorsService publicationAuthorsService,
                               PublicationService publicationService){
        this.studyService = studyService;
        this.ancestryRepository = ancestryRepository;
        this.lociAttributesService = lociAttributesService;
        this.associationService = associationService;
        this.publicationAuthorsService = publicationAuthorsService;
        this.publicationService = publicationService;
    }

    public void deleteNonPublicStudies(){

        List<Study> unpublishedStudies = studyService.deepFindUnPublishedStudies();

        getLog().info("Found " + unpublishedStudies.size() + " unpublished studies to be removed");


        unpublishedStudies.forEach(this::deleteStudy);

        getLog().info("Study deletion process successfully completed");
    }


    public void deletePublicationsWithoutStudies() {
        List<Publication>  pubs = publicationService.findAll();
        pubs.forEach(pub ->  {
            if(pub.getStudies().isEmpty()) {
                getLog().info(" Deleting Pmid without studies {}",pub.getPubmedId());
                publicationService.deletePublication(pub);
            }
        });

    }

    private void deleteStudy(Study study) {
        System.out.println("Removing study \t" + study.getPublicationId().getFirstAuthor().getFullname() + "\t (ID:" + study.getId() + ") with \t"
                               + study.getAssociations().size() + "\t association and \t"
                               + study.getAncestries().size() + "\t ancestries");
        getLog().debug("Removing study \t" + study.getPublicationId().getFirstAuthor().getFullname() + "\t (ID:" + study.getId() + ") with \t"
                               + study.getAssociations().size() + "\t association and \t"
                                + study.getAncestries().size() + "\t ancestries");


        // THOR - Don't delete the publication and Author - OR check if there is just a publication.
        Long publicationId = study.getPublicationId().getId();
        study.setPublicationId(null);
        studyService.setPublicationIdNull(study.getId());

        Collection<Association> associations = study.getAssociations();

        associations.forEach(this::deleteAssociation);

        Collection<Ancestry> ancestries = ancestryRepository.findByStudyId(study.getId());

        for (Ancestry ancestry : ancestries) {
            ancestryRepository.delete(ancestry);
        }

        // WeeklyTracking, CuratorTracking and Note. Please use this method!
        // Shared with === DataDeletionService ===
        studyService.deleteRelatedInfoByStudy(study);

        // Delete study
        studyService.deleteByStudyId(study.getId());

        //THOR - delete the publication if there are no studies related
        Publication publication = publicationService.deepFindPublicationbyId(publicationId);
        if (publication.getStudies().size() == 0) {
            publicationAuthorsService.deleteByPublication(publication);
            Boolean delete = publicationService.deletePublication(publication);
        }

    }

    public void deleteAssociation(Association association) {

        getLog().info("Deleting association ".concat(String.valueOf(association.getId())));
        //delete all the loci and risk alleles that are dependent on this association
        lociAttributesService.deleteLocusAndRiskAlleles(association);

        // Delete association
         associationService.deleteByAssociationId(association.getId());
    }



}
