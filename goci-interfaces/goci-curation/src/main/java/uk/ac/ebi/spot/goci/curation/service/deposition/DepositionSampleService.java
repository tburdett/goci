package uk.ac.ebi.spot.goci.curation.service.deposition;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.ac.ebi.spot.goci.model.*;
import uk.ac.ebi.spot.goci.model.deposition.DepositionSampleDto;
import uk.ac.ebi.spot.goci.repository.AncestralGroupRepository;
import uk.ac.ebi.spot.goci.repository.AncestryExtensionRepository;
import uk.ac.ebi.spot.goci.repository.AncestryRepository;
import uk.ac.ebi.spot.goci.repository.CountryRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class DepositionSampleService {

    private Logger log = LoggerFactory.getLogger(getClass());

    protected Logger getLog() {
        return log;
    }

    @Autowired
    CountryRepository countryRepository;
    @Autowired
    AncestryRepository ancestryRepository;
    @Autowired
    AncestralGroupRepository ancestralGroupRepository;
    @Autowired
    AncestryExtensionRepository extensionRepository;

    public DepositionSampleService() {
    }

    public String saveSamples(String studyTag, Study study, List<DepositionSampleDto> samples, ImportLog importLog) {
        //find samples in study
        StringBuffer studyNote = new StringBuffer();
        String initialSampleSize = "";
        String replicateSampleSize = "";
        for (DepositionSampleDto sampleDto : samples) {
            if (sampleDto.getStudyTag().equals(studyTag)) {
                ImportLogStep importStep = importLog.addStep(new ImportLogStep("Creating sample", study.getAccessionId()));

                Ancestry ancestry = new Ancestry();
                if (sampleDto.getStage().equalsIgnoreCase("Discovery")) {
                    ancestry.setType("initial");
                    initialSampleSize += buildDescription(sampleDto) + ", ";
                } else if (sampleDto.getStage().equalsIgnoreCase("Replication")) {
                    ancestry.setType("replication");
                    replicateSampleSize += buildDescription(sampleDto) + ", ";
                } else {
                    studyNote.append("unknown ancestry type: " + sampleDto.getStage());
                }
                List<Country> countryList = new ArrayList<>();
                String countryRecruitment = sampleDto.getCountryRecruitement();
                if (countryRecruitment != null) {
                    String[] countries = countryRecruitment.split("\\||,");
                    Map<String, String> countryMap = new HashMap<>();
                    for (String country : countries) {
                        countryMap.put(country.trim(), "");
                    }

                    for (String country : countryMap.keySet()) {
                        Country cCountry = countryRepository.findByCountryNameIgnoreCase(country);
                        if (cCountry == null) {
                            importLog.addError("Unable to find country: " + country, "Creating sample");
                        } else {
                            countryList.add(cCountry);
                        }
                    }
                }
                ancestry.setCountryOfRecruitment(countryList);
                if (sampleDto.getSize() != -1) {
                    ancestry.setNumberOfIndividuals(sampleDto.getSize());
                }
                ancestralGroupRepository.findByAncestralGroup(sampleDto.getAncestry());
                String ancestryCat = sampleDto.getAncestryCategory();
//                if(ancestryCat != null && !ancestryCat.endsWith("ancestry")){
//                    ancestryCat += " ancestry";
//                }
                List<AncestralGroup> ancestryGroups = new ArrayList<>();
                if (ancestryCat != null) {
                    String[] groups = ancestryCat.split("\\||,");
                    getLog().info("Ancestry groups provided: {}", ancestryCat);
                    for (String group : groups) {
                        AncestralGroup ancestryGroup = ancestralGroupRepository.findByAncestralGroup(group);
                        ancestryGroups.add(ancestryGroup);
                    }
                }
                getLog().info("Ancestry groups mapped: {}", ancestryGroups);
                ancestry.setAncestralGroups(ancestryGroups);
                ancestry.setStudy(study);
                ancestryRepository.save(ancestry);

                AncestryExtension ancestryExtension = new AncestryExtension();
                ancestryExtension.setAncestry(ancestry);
                if (sampleDto.getAncestry() != null) {
                    ancestryExtension.setAncestryDescriptor(sampleDto.getAncestry().replaceAll("\\|", ", "));
                }
                ancestryExtension.setIsolatedPopulation(sampleDto.getAncestryDescription());
                ancestryExtension.setNumberCases(sampleDto.getCases());
                ancestryExtension.setNumberControls(sampleDto.getControls());
                ancestryExtension.setSampleDescription(sampleDto.getSampleDescription());
                extensionRepository.save(ancestryExtension);
                ancestry.setAncestryExtension(ancestryExtension);
                ancestryRepository.save(ancestry);
                importLog.updateStatus(importStep.getId(), ImportLog.SUCCESS);
            }
        }
        if (initialSampleSize.endsWith(", ")) {
            initialSampleSize = initialSampleSize.substring(0, initialSampleSize.length() - 2);
        }
        if (replicateSampleSize.endsWith(", ")) {
            replicateSampleSize = replicateSampleSize.substring(0, replicateSampleSize.length() - 2);
        }
        studyNote.append("initial: " + initialSampleSize + "\n");
        studyNote.append("replication: " + replicateSampleSize + "\n");
        study.setInitialSampleSize(initialSampleSize.trim());
        study.setReplicateSampleSize(replicateSampleSize.trim());

        return studyNote.toString();
    }

    String buildDescription(DepositionSampleDto sampleDto) {
        String ancestry = null;
        if (sampleDto.getAncestry() != null) {
            ancestry = sampleDto.getAncestry().replaceAll("\\|", ", ");
        } else {
            ancestry = sampleDto.getAncestryCategory().replaceAll("\\|", ", ");
        }
        if (ancestry.trim().equalsIgnoreCase("NR")) {
            if (sampleDto.getCases() != null && sampleDto.getControls() != null && sampleDto.getCases() != 0 && sampleDto.getControls() != 0) {
                ancestry = String.format("%,d", sampleDto.getCases()) + " cases, " +
                        String.format("%,d", sampleDto.getControls()) + " controls";
            } else if (sampleDto.getSize() != -1) {
                ancestry = String.format("%,d", sampleDto.getSize()) + " individuals";
            } else {
                ancestry += " individuals";
            }
        } else {
            if (sampleDto.getCases() != null && sampleDto.getControls() != null && sampleDto.getCases() != 0 && sampleDto.getControls() != 0) {
                ancestry = String.format("%,d", sampleDto.getCases()) + " " + ancestry + " cases, " +
                        String.format("%,d", sampleDto.getControls()) + " " + ancestry + " controls";
            } else if (sampleDto.getSize() != -1) {
                ancestry = String.format("%,d", sampleDto.getSize()) + " " + ancestry + " individuals";
            } else {
                ancestry += " individuals";
            }
        }
        return ancestry;
    }

}
