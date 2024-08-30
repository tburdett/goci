package uk.ac.ebi.spot.goci.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.ac.ebi.spot.goci.model.Author;
import uk.ac.ebi.spot.goci.model.Publication;
import uk.ac.ebi.spot.goci.model.Study;
import uk.ac.ebi.spot.goci.repository.PublicationRepository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;

/**
 * PublicationService provides a list of methods to query the Repository
 *
 * @author Cinzia
 * @date 16/10/17
 */

@Service
public class PublicationService {

    private PublicationRepository publicationRepository;
    private StudyService studyService;
    private PublicationAuthorsService publicationAuthorsService;

    @Autowired
    public PublicationService(PublicationRepository publicationRepository,
                              StudyService studyService,
                              PublicationAuthorsService publicationAuthorsService) {
        this.publicationRepository = publicationRepository;
        this.studyService = studyService;
        this.publicationAuthorsService = publicationAuthorsService;
    }

    private Optional<Publication> getValue(Publication publication) {
        return (publication != null) ? Optional.of(publication) : Optional.empty();
    }


    public Optional<Publication> findOptionalById(Long id) {
        Publication publication = publicationRepository.findOne(id);
        return getValue(publication);
    }

    public Publication findById(Long id) {
        Optional<Publication> publication= findOptionalById(id);
        return (publication.isPresent()) ? publication.get() : null;
    }


    @Transactional(readOnly = true)
    public Optional<Publication> findOptionalByPubmedId(String pubmedId) {
        Publication publication = publicationRepository.findByPubmedId(pubmedId);
        return getValue(publication);
    }

    @Transactional(readOnly = true)
    public Optional<Collection<Study>> findOptionalStudiesByPubmedId(String pubmedId) {
        Publication publication = publicationRepository.findByPubmedId(pubmedId);
        return (publication != null) ? Optional.of(publication.getStudies()) : Optional.empty();
    }


    @Transactional(readOnly = true)
    public Publication findByPumedId(String pubmedId) {
        Optional<Publication> publication= findOptionalByPubmedId(pubmedId);
        return (publication.isPresent()) ? publication.get() : null;
    }

    public Publication createOrFindByPumedId(String pubmedId) {
        Optional<Publication> publication= findOptionalByPubmedId(pubmedId);
        return (publication.isPresent()) ? publication.get() : new Publication();
    }

    @Transactional(readOnly = true)
    public Collection<Study> findStudiesByPubmedId(String pubmedId) {
        Optional<Collection<Study>> listStudies= findOptionalStudiesByPubmedId(pubmedId);
        return (listStudies.isPresent())? listStudies.get() : null;
    }


    @Transactional(readOnly = true)
    public List<Publication> findAll() {
        Sort ascPubmedId = new Sort(new Sort.Order(Sort.Direction.DESC, "pubmedId"));
        return publicationRepository.findAll(ascPubmedId);
    }

    public List<String> findAllStudyAuthors() {
        return publicationRepository.findAllStudyAuthors();
    }

    public List<Publication> findByFirstAuthorIsNull() {
        return  publicationRepository.findByFirstAuthorIsNull();
    }

    public Publication findByStudyId(Long studyId) {
        Study study = studyService.findOne(studyId);
        return (study != null) ? study.getPublicationId() : null;
    }

    public void save(Publication publication) {
        publicationRepository.save(publication);
    }


    public void updatePublicationFirstAuthor(Publication publication, Author firstAuthor) {
        publication.setFirstAuthor(firstAuthor);
        save(publication);
    }

    public void deletePublicationWithoutStudies(Publication publication){
        publicationAuthorsService.deleteByPublication(publication);
        publicationRepository.delete(publication);
    }

    public Boolean deletePublication(Publication publication) {
        if (publication.getPublicationAuthors().size() == 0) {
            publicationRepository.delete(publication);
        }
        else {
            return false;
        }
        return true;
    }


    @Transactional(readOnly = true)
    public Publication deepFindPublicationbyId(Long publicationId) {

        Publication publication = publicationRepository.findOne(publicationId);
        this.loadAssociatedData(publication);
        return publication;
    }

    @Transactional(readOnly = true)
    public void loadAssociatedData(Publication publication) {
        int studiesCount = publication.getStudies().size();

        Collection<Author> authorArrayList = new ArrayList<>();
        // Extract the author in order
        publication.getPublicationAuthors().forEach(publicationAuthor ->{
            authorArrayList.add(publicationAuthor.getAuthor());
        });

    }



}
