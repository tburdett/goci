package uk.ac.ebi.spot.goci;

import org.joda.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.ac.ebi.spot.goci.model.*;
import uk.ac.ebi.spot.goci.model.deposition.*;
import uk.ac.ebi.spot.goci.model.deposition.util.*;
import uk.ac.ebi.spot.goci.repository.BodyOfWorkRepository;
import uk.ac.ebi.spot.goci.repository.UnpublishedAncestryRepository;
import uk.ac.ebi.spot.goci.repository.UnpublishedStudyRepository;
import uk.ac.ebi.spot.goci.service.DepositionPublicationService;
import uk.ac.ebi.spot.goci.service.DepositionSubmissionService;
import uk.ac.ebi.spot.goci.service.PublicationService;
import uk.ac.ebi.spot.goci.service.StudyService;
import uk.ac.ebi.spot.goci.service.email.DepositionSyncEmailService;
import uk.ac.ebi.spot.goci.util.DepositionUtil;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class DepositionSyncService {

    private final Logger log = LoggerFactory.getLogger(getClass());

    protected Logger getLog() {
        return log;
    }

    private static final List<String> INELIGIBLE_STATUSES = Arrays.asList(
            "curation abandoned",
            "cnv paper",
            "permanently unpublished from catalog"
    );

    private final DepositionSubmissionService submissionService;
    private final BodyOfWorkRepository bodyOfWorkRepository;
    private final UnpublishedStudyRepository unpublishedRepository;
    private final UnpublishedAncestryRepository unpublishedAncestryRepo;
    private final PublicationService publicationService;

    private final StudyService studyService;
    private final DepositionPublicationService depositionPublicationService;
    private final DepositionSyncEmailService depositionSyncEmailService;

    public DepositionSyncService(@Autowired PublicationService publicationService,
                                 @Autowired StudyService studyService,
                                 @Autowired DepositionPublicationService depositionPublicationService,
                                 @Autowired DepositionSubmissionService submissionService,
                                 @Autowired DepositionSyncEmailService depositionSyncEmailService,
                                 @Autowired BodyOfWorkRepository bodyOfWorkRepository,
                                 @Autowired UnpublishedStudyRepository unpublishedRepository,
                                 @Autowired UnpublishedAncestryRepository unpublishedAncestryRepo) {
        this.depositionPublicationService = depositionPublicationService;
        this.publicationService = publicationService;
        this.studyService = studyService;
        this.submissionService = submissionService;
        this.depositionSyncEmailService = depositionSyncEmailService;
        this.bodyOfWorkRepository = bodyOfWorkRepository;
        this.unpublishedRepository = unpublishedRepository;
        this.unpublishedAncestryRepo = unpublishedAncestryRepo;
    }

    private boolean isPublished(Publication publication) {
        for (Study study : publication.getStudies()) {
//            boolean studyPublished = false;
            Housekeeping housekeeping = study.getHousekeeping();
            if (housekeeping.getIsPublished()) {
                return true;
/*
                studyPublished = true;
            } else if (!housekeeping.getIsPublished() && housekeeping.getCatalogUnpublishDate() != null) {
                studyPublished = true;
            }
            if (!studyPublished) {
                return false;
*/
            }
        }
        return false;
    }

    /**
     * syncPublications intends to keep publications syned between GOCI and Deposition. It checks the GOCI catalog
     * against Deposition. If a publication is in GOCI but not in Deposition, it checks the state of the studies in
     * GOCI.
     * If the study is awaiting curation (assigned to Level 1 Curator + Awating Curation status), the publication is
     * sent
     * to Deposition as AVAILABLE. If the study is published (Publish Study status) the publication is sent to
     * Deposition
     * as EXPORTED and a submission is created containing the study data from GOCI.
     * This is the reverse of the Import endpoint, where a submission is received from Curation and added into GOCI.
     */
    public void syncPublications(boolean initialSync) {
        SyncLog syncLog = new SyncLog();

        try {
            //read all publications from GOCI
            List<Publication> gociPublications = publicationService.findAll();
            //if publication not in Deposition, insert
            Map<String, DepositionPublication> depositionPublications = depositionPublicationService.getAllPublications();
            Map<String, BodyOfWorkDto> bomMap = depositionPublicationService.getAllBodyOfWork();
            for (Publication p : gociPublications) {
                String pubmedId = p.getPubmedId();
                //System.out.println("checking pmid " + pubmedId);
                DepositionPublication newPublication = createPublication(p);
                if (newPublication == null) {
                    System.out.println("ERROR: Unable to process publication: " + pubmedId + ". Could not create new publication object.");
                    continue;
                }
                DepositionPublication depositionPublication = depositionPublications.get(pubmedId);

                boolean isPublished = isPublished(p);
                boolean isValid = isValid(p);
                boolean hasSS = addSummaryStatsData(newPublication, p);
                boolean bomAssoc = isUnpublished(p, bomMap.values());

                newPublication.setStatus("ELIGIBLE");
                if (isPublished) {
                    newPublication.setStatus("PUBLISHED");
                    if (hasSS) {
                        newPublication.setStatus("PUBLISHED_WITH_SS");
                    }
                } else {
                    if (!isValid) {
                        getLog().info("Publication NOT ELIGIBLE: {}", pubmedId);
                        if (depositionPublication != null) {
                            getLog().info("Attempting to delete publication from the Deposition App.");
                            if (depositionPublication.getStatus().startsWith("UNDER") || depositionPublication.getStatus().startsWith("CURATION")) {
                                syncLog.addError(pubmedId, "Publication retired has an incompatible status in Deposition: " + depositionPublication.getStatus());
                                continue;
                            }

                            depositionPublicationService.deletePublication(depositionPublication);
                            syncLog.addRetired(pubmedId, getInvalidStatus(p));
                        }

                        continue;
                    }
                }
                if (bomAssoc) {
                    newPublication.setStatus("UNDER_SUBMISSION");
                }
                if (initialSync) { // add all publications to mongo
                    if (depositionPublication == null) {
                        getLog().info("Sending publication [{}] with status: {}", pubmedId, newPublication.getStatus());
                        depositionPublicationService.addPublication(newPublication);
                    }
                } else {
                    if (depositionPublication == null) { // add new publication
                        getLog().info("Sending new publication [{}] with status: {}", pubmedId, newPublication.getStatus());
                        depositionPublicationService.addPublication(newPublication);
                        syncLog.addNewPublication(pubmedId, newPublication.getStatus());
                    } else {
                        if (depositionPublication.getStatus().equalsIgnoreCase("UNDER_SUBMISSION") && (!isPublished || hasSS)
                                || depositionPublication.getStatus().equalsIgnoreCase("UNDER_SUMMARY_STATS_SUBMISSION")) {
                            continue;
                        }

                        if (!newPublication.getStatus().equalsIgnoreCase(depositionPublication.getStatus())) {
                            getLog().info("[Status change] Updating publication [{}] with status: {}", pubmedId, newPublication.getStatus());
                            newPublication.setFirstAuthor(p.getFirstAuthor().getFullnameStandard());
                            depositionPublicationService.updatePublication(newPublication);
                            if (newPublication.getStatus().equalsIgnoreCase("PUBLISHED")) {
                                syncLog.addPublishedPublication();
                            } else {
                                if (newPublication.getStatus().equalsIgnoreCase("PUBLISHED_WITH_SS")) {
                                    syncLog.addSSPublication();
                                } else {
                                    syncLog.addEligiblePublication();
                                }
                            }
                        } else {
                            if (hasSS) {
                                getLog().info("[Same status] Updating publication [{}] with status: {}", pubmedId, newPublication.getStatus());
                                newPublication.setFirstAuthor(p.getFirstAuthor().getFullnameStandard());
                                depositionPublicationService.updatePublication(newPublication);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            getLog().error("Encountered error: {}", e.getMessage(), e);

            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            pw.flush();
            syncLog.addError("PROCESS", sw.toString());
            pw.close();
        }

        depositionSyncEmailService.sendSyncNotification(syncLog.getLog());
    }

    private boolean isValid(Publication publication) {
        int totalCount = 0;
        int ineligible = 0;
        for (Study study : publication.getStudies()) {
            totalCount++;
            Housekeeping housekeeping = study.getHousekeeping();
            if (INELIGIBLE_STATUSES.contains(housekeeping.getCurationStatus().getStatus().toLowerCase())) {
                ineligible++;
            }
        }
        return totalCount != ineligible || totalCount == 0;
    }

    private String getInvalidStatus(Publication publication) {
        for (Study study : publication.getStudies()) {
            Housekeeping housekeeping = study.getHousekeeping();
            if (INELIGIBLE_STATUSES.contains(housekeeping.getCurationStatus().getStatus().toLowerCase())) {
                return housekeeping.getCurationStatus().getStatus();
            }
        }
        return null;
    }

    /**
     * method to import new studies from deposition that do not have a PubMed ID. We want to keep them separate from
     * existing studies to indicate the different level of review. But we do want them stored in GOCI so they can be
     * searched and displayed.
     */
    public void syncUnpublishedStudies() {
        ImportLog importLog = new ImportLog();
        List<String> newPubs = new ArrayList<>();
        List<String> updatePubs = new ArrayList<>();
        Map<String, DepositionSubmission> submissions = submissionService.getSubmissions();
        getLog().info("Found {} submissions.", submissions.size());
        //if unpublished_studies does not have accession, add
        //check body of work, if not found, add
        //else if accession exists, check publication for change, update
        //curation import will need to prune unpublished_studies, ancestry and body_of_work
        submissions.forEach((s, submission) -> {
            Submission.SubmissionType submissionType = DepositionUtil.getSubmissionType(submission);
            List<DepositionStudyDto> studyDtos = getStudies(submission);
            if (submission.getStatus().equals("SUBMITTED") && submissionType == Submission.SubmissionType.SUM_STATS) {
                autoImportSumStatsSubmission(submission, importLog, studyDtos);
            }

            if (submission.getStatus().equals("DEPOSITION_COMPLETE") && submission.getProvenanceType().equals("BODY_OF_WORK")) {
                getLog().info("Found new SUBMITTED & BODY_OF_WORK submission: {} | {}", submission.getSubmissionId(), submission.getBodyOfWork().getBodyOfWorkId());
                BodyOfWorkDto bodyOfWorkDto = submission.getBodyOfWork();
                if (isEligible(bodyOfWorkDto)) {
                    getLog().info(" - Submission [{}] is eligible.", submission.getSubmissionId());
                    Set<UnpublishedStudy> studies = new HashSet<>();
                    List<DepositionSampleDto> sampleDtoList = getSamples(submission.getSubmissionId());
                    studyDtos.forEach(studyDto -> {
                        String studyTag = studyDto.getStudyTag();
                        getLog().info(" - Processing study tag: {}", studyTag);
                        UnpublishedStudy unpublishedStudy = unpublishedRepository.findByAccession(studyDto.getAccession());
                        if (unpublishedStudy == null) {
                            getLog().info(" - Study tag [{}] not found. Adding it to DB.", studyTag);
                            unpublishedStudy =
                                    unpublishedRepository.save(UnpublishedStudy.createFromStudy(studyDto, submission));
                            studies.add(unpublishedStudy);
                            //add ancestries
                            List<DepositionSampleDto> filteredSamplesList = filterSamplesBasedonStudyTag(studyTag, sampleDtoList);
                            List<UnpublishedAncestry> ancestryList = new ArrayList<>();
                            for (DepositionSampleDto sampleDto : filteredSamplesList) {
                                UnpublishedAncestry ancestry = UnpublishedAncestry.create(sampleDto, unpublishedStudy);
                                ancestryList.add(ancestry);
                            }
                            getLog().info(" - Study tag [{}] has {} ancestries.", studyTag, ancestryList.size());
                            unpublishedAncestryRepo.save(ancestryList);
                            unpublishedStudy.setAncestries(ancestryList);
                            unpublishedRepository.save(unpublishedStudy);
                        }
                    });
                    getLog().info(" - Looking for body of work [{}] in the DB.", bodyOfWorkDto.getBodyOfWorkId());
                    BodyOfWork bom = bodyOfWorkRepository.findByPublicationId(bodyOfWorkDto.getBodyOfWorkId());
                    if (bom == null) {
                        getLog().info(" - Body of work [{}] not found. Adding to DB.", bodyOfWorkDto.getBodyOfWorkId());
                        //add bodies of work
                        BodyOfWork bodyOfWork = BodyOfWork.create(bodyOfWorkDto);
                        bodyOfWork.setStudies(studies);
                        bodyOfWorkRepository.save(bodyOfWork);
                        newPubs.add(bodyOfWorkDto.getBodyOfWorkId());
                    }
                }
            }
        });
        Map<String, BodyOfWorkDto> bomMap = depositionPublicationService.getAllBodyOfWork();
        getLog().info("Processing remaining body of works: {}", bomMap.size());
        bomMap.entrySet().stream().forEach(e -> {
            String bomId = e.getKey();
            BodyOfWorkDto dto = e.getValue();
            BodyOfWork bom = bodyOfWorkRepository.findByPublicationId(bomId);
            getLog().info("Body of work [{}] exists in the DB: {}", bomId, (bom != null));
            BodyOfWork newBom = BodyOfWork.create(dto);
            if (bom != null && isEligible(dto) && !bom.equals(newBom)) {
                getLog().info("Body of work [{}] present in the DB and requires updating.", bomId);
                bom.update(newBom);
                bodyOfWorkRepository.save(bom);
                updatePubs.add(dto.getBodyOfWorkId());
            }
        });
        System.out.println("created " + newPubs.size());
        System.out.println(Arrays.toString(newPubs.toArray()));
        System.out.println("updated " + updatePubs.size());
        System.out.println(Arrays.toString(updatePubs.toArray()));
        depositionSyncEmailService.sendImportNotification(importLog.getLog());
    }

    private void autoImportSumStatsSubmission(DepositionSubmission submission, ImportLog importLog, List<DepositionStudyDto> studyDtos) {
        getLog().info("Publishing summary stats: {} | {}", submission.getSubmissionId(), submission.getPublication().getPmid());
        List<String> errors = new ArrayList<>();
        getLog().info("[{}] Looking for studies in the local DB ...", submission.getSubmissionId());
        Collection<Study> dbStudies = studyService.findByPublication(submission.getPublication().getPmid());

        List<Long> studyIds = new ArrayList<>();
        for (Study study : dbStudies) {
            studyIds.add(study.getId());
        }
        try {
            for (DepositionStudyDto studyDto : studyDtos) {
                String tag = studyDto.getStudyTag();
                boolean match = false;
                for (Long studyId : studyIds) {
                    Study study = studyService.findOne(studyId);
                    if (study.getAccessionId().equals(studyDto.getAccession())) {
                        study.setFullPvalueSet(true);
                        study.setStudyTag(tag);
                        study.setAgreedToCc0(studyDto.getAgreedToCc0());
                        studyService.save(study);
                        match = true;
                    }
                }
                if (!match) {
                    getLog().error("Study [{}] has no matching GCST in the DB.", studyDto.getAccession());
                    errors.add("Study [" + studyDto.getAccession() + "] has no matching GCST in the DB.");
                }
            }
            getLog().info("Publishing summary stats done.");
            submissionService.updateSubmission(submission, "CURATION_COMPLETE");
        } catch (Exception e) {
            getLog().error("Encountered error: {}", e.getMessage(), e);
            errors.add("Error: " + e.getMessage());
        }
        importLog.addEntry(submission.getSubmissionId(), submission.getPublication().getPmid(), errors);
    }

    private List<DepositionSampleDto> getSamples(String submissionImportId) {
        List<DepositionSampleDto> sampleDtoList = new ArrayList<>();
        DepositionSampleListWrapper depositionSampleListWrapper = null;
        depositionSampleListWrapper = submissionService.getSamples("", String.valueOf(submissionImportId));
        List<DepositionSampleDto> samples = buildSamplesList(depositionSampleListWrapper);
        if(samples != null) {
            sampleDtoList.addAll(samples);
        }
        Links sampleLinks = depositionSampleListWrapper.getLinks();
        while(sampleLinks != null && sampleLinks.getNext() != null) {
            depositionSampleListWrapper = submissionService.getSamples(sampleLinks.getNext().getHref(), String.valueOf(submissionImportId));
            samples = buildSamplesList(depositionSampleListWrapper);
            if(samples != null) {
                sampleDtoList.addAll(samples);
            }
            sampleLinks = depositionSampleListWrapper.getLinks();
        }
   /*   return sampleDtoList.stream()
                .filter(depositionSampleDto -> depositionSampleDto.getStudyTag().equals(studyTag))
                        .collect(Collectors.toList());*/
        return sampleDtoList;
    }

    private List<DepositionSampleDto> filterSamplesBasedonStudyTag(String studyTag, List<DepositionSampleDto> sampleDtoList) {
        return sampleDtoList.stream()
                .filter(depositionSampleDto -> depositionSampleDto.getStudyTag().equals(studyTag))
                .collect(Collectors.toList());
    }

    private boolean addSummaryStatsData(DepositionPublication depositionPublication, Publication publication) {
        boolean hasFiles = false;
        List<DepositionSummaryStatsDto> summaryStatsDtoList = new ArrayList<>();
        Collection<Study> studies = publication.getStudies();
        if (studies == null) {
            return hasFiles;
        }
        for (Study study : studies) {
            if (study.getAccessionId() != null) {
                DepositionSummaryStatsDto summaryStatsDto = new DepositionSummaryStatsDto();
                summaryStatsDto.setStudyAccession(study.getAccessionId());
                summaryStatsDto.setSampleDescription(study.getInitialSampleSize());
                if (study.getDiseaseTrait() != null) {
                    summaryStatsDto.setTrait(study.getDiseaseTrait().getTrait());
                }
                summaryStatsDto.setStudyTag(study.getStudyTag());
                if (study.getFullPvalueSet()) {
                    hasFiles = true;
                }
                summaryStatsDto.setHasSummaryStats(study.getFullPvalueSet());
                summaryStatsDtoList.add(summaryStatsDto);
            }
        }
        if (summaryStatsDtoList.size() != 0) {
            depositionPublication.setSummaryStatsDtoList(summaryStatsDtoList);
        }
        return hasFiles;
    }

    /**
     * fix publications is intended as a one-off execution to correct errors with loaded data, not as part of the
     * daily sync
     */
    public void fixPublications() {
        List<String> fixedPubs = new ArrayList<>();
        //read all publications from GOCI
        List<Publication> gociPublications = publicationService.findAll();
        //check status, set to PUBLISHED_SS if hasSummaryStats
        Map<String, DepositionSubmission> submissions = submissionService.getSubmissions();
        Map<String, DepositionPublication> publicationMap = buildPublicationMap(submissions);
        Map<String, List<String>> sumStatsMap = buildSumStatsMap(submissions);
        System.out.println("pmid\tis published\tdepo sum stats size\tgoci sum stats size");
        for (Publication p : gociPublications) {
            String pubmedId = p.getPubmedId();
            List<String> accessionList = sumStatsMap.get(pubmedId);
            DepositionPublication depositionPublication = publicationMap.get(pubmedId);
            boolean isPublished = isPublished(p);
            if (depositionPublication != null) {
                addSummaryStatsData(depositionPublication, p);
                int newSumStatsSize = depositionPublication.getSummaryStatsDtoList() != null ?
                        depositionPublication.getSummaryStatsDtoList().size() : 0;
                System.out.println(pubmedId + "\t" + isPublished + "\t" + accessionList.size() + "\t" + newSumStatsSize);
                if (accessionList.size() != newSumStatsSize) {
                    System.out.println("adding summary stats to " + depositionPublication.getPmid());
                    depositionPublicationService.updatePublication(depositionPublication);
                    fixedPubs.add(pubmedId);
                }
            }
        }
        System.out.println("fixed " + fixedPubs.size());
        System.out.println(Arrays.toString(fixedPubs.toArray()));

    }

    private Map<String, DepositionPublication> buildPublicationMap(Map<String, DepositionSubmission> submissions) {
        Map<String, DepositionPublication> publicationMap = new HashMap<>();
        submissions.forEach((s, submission) -> {
            if (submission.getPublication() != null) {
                publicationMap.put(submission.getPublication().getPmid(),
                        submission.getPublication());
            }
        });
        return publicationMap;
    }

    private Map<String, List<String>> buildSumStatsMap(Map<String, DepositionSubmission> submissions) {
        Map<String, List<String>> sumStatsMap = new HashMap<>();
        submissions.forEach((s, submission) -> {
            if (submission.getPublication() != null) {
                List<String> sumStatsList = new ArrayList<>();
                submission.getStudies().forEach(studyDto -> {
                    if (studyDto.getAccession() != null) {
                        sumStatsList.add(studyDto.getAccession());
                    }
                });
                sumStatsMap.put(submission.getPublication().getPmid(), sumStatsList);
            }
        });
        return sumStatsMap;
    }

    private DepositionPublication createPublication(Publication p) {
        Author author = p.getFirstAuthor();
        DepositionPublication newPublication = null;
        if (author != null) { //error check for invalid publications
            newPublication = new DepositionPublication();
            newPublication.setPmid(p.getPubmedId());
            String authorName = null;
            Author firstAuthor = p.getFirstAuthor();
            if (firstAuthor.getLastName() == null || firstAuthor.getInitials() == null) {
                authorName = firstAuthor.getFullname();
            } else {
                authorName = firstAuthor.getLastName() + " " + firstAuthor.getInitials();
            }
            newPublication.setFirstAuthor(authorName);
            newPublication.setPublicationDate(new LocalDate(p.getPublicationDate()));
            newPublication.setPublicationId(p.getId().toString());
            newPublication.setTitle(p.getTitle());
            newPublication.setJournal(p.getPublication());
            newPublication.setStatus("ELIGIBLE");
        } else {
            System.out.println("error: publication " + p.getPubmedId() + " has no authors");
        }
        return newPublication;
    }

    private boolean isEligible(BodyOfWorkDto bodyOfWorkDto) {
        if (!bodyOfWorkDto.getStatus().equals("UNDER_SUBMISSION")) {
            return false;
        }
        if (bodyOfWorkDto.getEmbargoUntilPublished() != null && bodyOfWorkDto.getEmbargoUntilPublished() == true && bodyOfWorkDto.getPmids() == null) {
            return false;
        }
        if (bodyOfWorkDto.getEmbargoDate() != null && new LocalDate().isBefore(bodyOfWorkDto.getEmbargoDate())) {
            return false;
        } else {
            return true;
        }
    }

    private boolean isUnpublished(Publication publication, Collection<BodyOfWorkDto> bomList) {
        if (bodyOfWorkRepository.findByPubMedId(publication.getPubmedId()) != null) {
            return true;
        } else {
            List<BodyOfWork> bodiesOfWork = bodyOfWorkRepository.findAll();
            for (BodyOfWork bom : bodiesOfWork) {
                if (publication.getPubmedId().equals(bom.getPubMedId())) {
                    return true;
                } else if (bom.getPubMedId() != null && bom.getPubMedId().contains(publication.getPubmedId())) {
                    return true;
                }
            }
            for (BodyOfWorkDto bom : bomList) {
                if (bom.getPmids() != null) {
                    String bomId = String.join(",", bom.getPmids());
                    if (bomId.contains(publication.getPubmedId())) {
                        return true;
                    }
                }
            }

        }
        return false;
    }


    private List<DepositionStudyDto> getStudies(DepositionSubmission depositionSubmission) {
        List<DepositionStudyDto> studyDtos = new ArrayList<>();
        DepositionStudyListWrapper depositionStudyListWrapper = null;
        depositionStudyListWrapper = submissionService.getSubmissionStudies("", String.valueOf(depositionSubmission.getSubmissionId()));
        List<DepositionStudyDto> studies = buildStudiesList(depositionStudyListWrapper);
        if(studies != null) {
            studyDtos.addAll(studies);
        }
        Links links = depositionStudyListWrapper.getLinks();
        while(links != null && links.getNext() != null) {
            depositionStudyListWrapper = submissionService.getSubmissionStudies(links.getNext().getHref(), String.valueOf(depositionSubmission.getSubmissionId()));
            studies = buildStudiesList(depositionStudyListWrapper);
            if(studies != null) {
                studyDtos.addAll(studies);
            }
            links = depositionStudyListWrapper.getLinks();
        }
        return studyDtos;
    }

    private List<DepositionSampleDto> buildSamplesList(DepositionSampleListWrapper depositionSampleListWrapper) {
        return Optional.ofNullable(depositionSampleListWrapper.getSamplesList())
                .map(DepositionSampleList::getSamples)
                .orElse(null);
    }

    private List<DepositionStudyDto> buildStudiesList(DepositionStudyListWrapper depositionStudyListWrapper) {
        return Optional.ofNullable(depositionStudyListWrapper.getStudies())
                .map(DepositionStudyList::getStudies)
                .orElse(null);
    }
}
