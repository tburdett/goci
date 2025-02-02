package uk.ac.ebi.spot.goci.curation.controller.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.MediaTypes;
import org.springframework.hateoas.mvc.ControllerLinkBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import uk.ac.ebi.spot.goci.curation.constants.Endpoint;
import uk.ac.ebi.spot.goci.curation.controller.assembler.PublicStudyAssembler;
import uk.ac.ebi.spot.goci.curation.dto.StudyDto;
import uk.ac.ebi.spot.goci.curation.dto.StudyPatchRequest;
import uk.ac.ebi.spot.goci.curation.dto.FileUploadRequest;
import uk.ac.ebi.spot.goci.curation.exception.FileValidationException;
import uk.ac.ebi.spot.goci.curation.service.StudyDataService;
import uk.ac.ebi.spot.goci.curation.util.FileHandler;
import uk.ac.ebi.spot.goci.model.Study;

import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping(Endpoint.API_V1)
public class StudyRestController {

    private Logger log = LoggerFactory.getLogger(this.getClass());

    private StudyDataService studyDataService;
    private PublicStudyAssembler publicStudyAssembler;

    public StudyRestController(StudyDataService studyDataService,
                               PublicStudyAssembler publicStudyAssembler) {
        this.studyDataService = studyDataService;
        this.publicStudyAssembler = publicStudyAssembler;
    }

    public Object getOneStudy(Long id) {
        return "";
    }

    @GetMapping(value = Endpoint.PUBLIC_STUDIES, produces = MediaTypes.HAL_JSON_VALUE)
    public Object getStudies(PagedResourcesAssembler<Study> assembler,
                             @PageableDefault(size = 20) Pageable pageable) {

        Page<Study> studies = studyDataService.getStudiesByHousekeepingStatus(pageable, true);
        //Page<Study> studies = studyDataService.getStudies(pageable);
        final ControllerLinkBuilder linkBuilder = ControllerLinkBuilder.linkTo(
                ControllerLinkBuilder
                        .methodOn(StudyRestController.class)
                        .getStudies(assembler, pageable)
        );
        return assembler.toResource(studies, publicStudyAssembler, linkBuilder.withSelfRel());
    }

    @PostMapping(Endpoint.STUDIES)
    public Object multiUploadFileModel(@Valid FileUploadRequest fileUploadRequest, BindingResult result) {
        if (result.hasErrors()) {
            throw new FileValidationException(result);
        }
        List<StudyPatchRequest> studyPatchRequests = FileHandler.getStudyPatchRequests(fileUploadRequest);
        List<Study> updatedStudies = new ArrayList<>();
        for (StudyPatchRequest request : studyPatchRequests) {
            log.info("Dataset with GCST {} and Curated Trait {} was loaded from file", request.getGcst(), request.getCuratedReportedTrait());
            String gcst = request.getGcst().trim();
            String curatedReportedTrait = request.getCuratedReportedTrait().trim();
            updatedStudies.add(studyDataService.updateStudyDiseaseTraitByAccessionId(curatedReportedTrait, gcst));
        }
        return new ResponseEntity<>(updatedStudies, HttpStatus.OK);
    }

    @GetMapping(Endpoint.STUDIES+"/export")
    public String allStudies(HttpServletResponse response,
                             PagedResourcesAssembler<Study> assembler,
                             @RequestParam(value = "pubmed", defaultValue = "*") String pubmedId,
                             @RequestParam(value = "author", defaultValue = "*") String author,
                             @RequestParam(value = "studytype", defaultValue = "*") String studyType,
                             @RequestParam(value = "efotraitid", defaultValue = "0") Long efoTraitId,
                             @RequestParam(value = "diseasetraitid", defaultValue = "0") Long diseaseTraitId,
                             @RequestParam(value = "notesquery", defaultValue = "*") String notesQuery,
                             @RequestParam(value = "status", defaultValue = "0") Long status,
                             @RequestParam(value = "curator", defaultValue = "0") Long curator,
                             @RequestParam(defaultValue = "*") String gcstId,
                             @RequestParam(defaultValue = "0") Long studyId) throws IOException {

        Pageable pageable = new PageRequest(0, 1000);

        List<StudyDto> studyDtos = studyDataService.getAllStudies(pubmedId, author, studyType, efoTraitId, diseaseTraitId, notesQuery,
                                                      status, curator, gcstId, studyId, pageable);

        response.setContentType("text/csv;charset=utf-8");
        response.setHeader("Content-Disposition", "attachment; filename=studies.tsv");
        response.getOutputStream().flush();
        return FileHandler.serializePojoToTsv(studyDtos);

    }
}
