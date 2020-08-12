package uk.ac.ebi.spot.goci.curation.service.deposition;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.ac.ebi.spot.goci.curation.model.AssociationValidationView;
import uk.ac.ebi.spot.goci.curation.service.AssociationOperationsService;
import uk.ac.ebi.spot.goci.curation.service.SingleSnpMultiSnpAssociationService;
import uk.ac.ebi.spot.goci.exception.EnsemblMappingException;
import uk.ac.ebi.spot.goci.model.*;
import uk.ac.ebi.spot.goci.model.deposition.DepositionAssociationDto;
import uk.ac.ebi.spot.goci.repository.AssociationExtensionRepository;
import uk.ac.ebi.spot.goci.repository.AssociationRepository;
import uk.ac.ebi.spot.goci.service.EnsemblRestTemplateService;
import uk.ac.ebi.spot.goci.service.LociAttributesService;
import uk.ac.ebi.spot.goci.service.MapCatalogService;
import uk.ac.ebi.spot.goci.service.ValidationService;
import uk.ac.ebi.spot.goci.utils.AssociationCalculationService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Component
public class DepositionAssociationService {

    private Logger log = LoggerFactory.getLogger(getClass());

    protected Logger getLog() {
        return log;
    }

    @Autowired
    SingleSnpMultiSnpAssociationService singleSnpMultiSnpAssociationService;
    @Autowired
    AssociationOperationsService associationOperationsService;
    @Autowired
    AssociationRepository associationRepository;
    @Autowired
    LociAttributesService lociService;
    @Autowired
    MapCatalogService mapCatalogService;
    @Autowired
    AssociationExtensionRepository extensionRepository;
    @Autowired
    AssociationCalculationService calculationService;
    @Autowired
    EnsemblRestTemplateService ensemblRestTemplateService;
    @Autowired
    ValidationService validationService;


    public DepositionAssociationService() {
    }

    public String saveAssociations(SecureUser currentUser, String studyTag, Study study, List<DepositionAssociationDto> associations, ImportLog importLog) {
        //find associations in study
        String eRelease = ensemblRestTemplateService.getRelease();
        StringBuffer studyNote = new StringBuffer();
        for (DepositionAssociationDto associationDto : associations) {
            if (associationDto.getStudyTag().equals(studyTag)) {
                getLog().info("Creating association: {} | {}", study.getStudyTag(), study.getAccessionId());
                ImportLogStep importStep = importLog.addStep(new ImportLogStep("Creating association", study.getAccessionId()));
                Association association = new Association();
                association.setSnpInteraction(false);
                Collection<Locus> loci = new ArrayList<>();
                Locus locus = new Locus();
                association.setMultiSnpHaplotype(false);
                association.setSnpType("novel");
                locus.setDescription("Single variant");

                if (associationDto.getStandardError() != null) {
                    association.setStandardError(associationDto.getStandardError().floatValue());
                }
                String pValue = associationDto.getPValue();
                if (pValue != null && pValue.toLowerCase().contains("e")) {
                    String[] pValues = pValue.toLowerCase().split("e");
                    int exponent = Integer.valueOf(pValues[1]);
                    int mantissa = (int) Math.round(Double.valueOf(pValues[0]));
                    association.setPvalueExponent(exponent);
                    association.setPvalueMantissa(mantissa);
                }
                association.setPvalueDescription(associationDto.getPValueText());
                String rsID = associationDto.getVariantID();
                getLog().info("Processing rdID: {}", rsID);
                if (rsID != null) {
                    SingleNucleotidePolymorphism snp = lociService.createSnp(rsID);
                    studyNote.append("added SNP " + rsID + "\n");//does this fail
                    RiskAllele riskAllele =
                            lociService.createRiskAllele(rsID + "-" + associationDto.getEffectAllele(), snp);
                    if (associationDto.getProxyVariant() != null) {
                        List<SingleNucleotidePolymorphism> proxySnps = new ArrayList<>();
                        proxySnps.add(lociService.createSnp(associationDto.getProxyVariant()));
                        riskAllele.setProxySnps(proxySnps);
                    }
                    List<RiskAllele> alleleList = new ArrayList<>();
                    alleleList.add(riskAllele);
                    locus.setStrongestRiskAlleles(alleleList);
                    loci.add(locus);
                    association.setLoci(loci);
                } else {
                    importLog.addError("No rsId provided.", "Creating association");
                    importLog.updateStatus(importStep.getId(), ImportLog.FAIL);
                    continue;
                }
                if (associationDto.getEffectAlleleFrequency() != null && associationDto.getEffectAlleleFrequency().intValue() != -1) {
                    association.setRiskFrequency(associationDto.getEffectAlleleFrequency().toString());
                } else {
                    association.setRiskFrequency("NR");
                }
                if (associationDto.getStandardError() != null) {
                    association.setStandardError(associationDto.getStandardError().floatValue());
                }
                if (associationDto.getOddsRatio() != null) {
                    association.setOrPerCopyNum(associationDto.getOddsRatio().floatValue());
                }
                if (associationDto.getBeta() != null) {
                    Double betaValue = associationDto.getBeta();
                    if (betaValue < 0) {
                        association.setBetaDirection("decrease");
                    } else {
                        association.setBetaDirection("increase");
                    }
                    association.setBetaNum(Math.abs(betaValue.floatValue()));
                    association.setBetaUnit(associationDto.getBetaUnit());
                }
                if (associationDto.getCiLower() != null && associationDto.getCiUpper() != null) {
                    association.setRange("[" + associationDto.getCiLower() + "-" + associationDto.getCiUpper() + "]");
                } else {
                    if (associationDto.getOddsRatio() != null && associationDto.getStandardError() != null) {
                        association.setRange(calculationService
                                .setRange(associationDto.getStandardError(), Math.abs(associationDto.getOddsRatio())));
                    } else if (associationDto.getBeta() != null && associationDto.getStandardError() != null) {
                        association.setRange(calculationService
                                .setRange(associationDto.getStandardError(), Math.abs(associationDto.getBeta())));
                    }
                }
                AssociationExtension associationExtension = new AssociationExtension();
                associationExtension.setAssociation(association);
                associationExtension.setEffectAllele(associationDto.getEffectAllele());
                associationExtension.setOtherAllele(associationDto.getOtherAllele());

                associationOperationsService.saveAssociation(association, study, new ArrayList<>());
                extensionRepository.save(associationExtension);
                association.setAssociationExtension(associationExtension);
                associationRepository.save(association);

                Collection<AssociationValidationView> errors = associationOperationsService.saveAssociationCreatedFromForm(study,
                        association, currentUser, eRelease);
                getLog().info("Found {} errors on save.", errors.size());
                StringBuffer errorBuffer = new StringBuffer();
                for (AssociationValidationView associationValidationView : errors) {
                    getLog().error("Save error: {} | {} | {}", associationValidationView.getWarning(), associationValidationView.getErrorMessage(), associationValidationView.getField());
                    errorBuffer.append("Save error: " + associationValidationView.getWarning() + " | " +
                            associationValidationView.getErrorMessage() + " | " + associationValidationView.getField()).append("\n");
                }
                if (errors.isEmpty()) {
                    try {
                        mapCatalogService.mapCatalogContentsByAssociations(currentUser.getEmail(),
                                Collections.singleton(association));
                        studyNote.append("mapped associations" + "\n");
                    } catch (EnsemblMappingException e) {
                        getLog().error("Ensembl mapping failure: {}", e.getMessage(), e);
                        importLog.addError("Ensembl mapping failure: " + e.getMessage(), "Creating association");
                        importLog.updateStatus(importStep.getId(), ImportLog.FAIL);
                        continue;
                    }
                    importLog.updateStatus(importStep.getId(), ImportLog.SUCCESS);
                } else {
                    importLog.addError(errorBuffer.toString().trim(), "Creating association");
                    importLog.updateStatus(importStep.getId(), ImportLog.FAIL);
                }
            }
        }
        return studyNote.toString();
    }
}
