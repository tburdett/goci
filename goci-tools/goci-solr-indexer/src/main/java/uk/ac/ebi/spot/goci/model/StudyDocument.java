package uk.ac.ebi.spot.goci.model;

import org.apache.commons.lang3.StringUtils;
import org.apache.solr.client.solrj.beans.Field;

import javax.persistence.CollectionTable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Javadocs go here!
 *
 * @author Tony Burdett
 * @date 23/12/14
 *
 */
public class StudyDocument extends OntologyEnabledDocument<Study> {
    // basic study information
    @Field private String pubmedId;
    @Field private String title;
    @Field private String author;
    @Field private String authorAscii;
    @Field private String orcid;
    @Field private String publication;
    @Field private String publicationDate;
    @Field private String catalogPublishDate;
    @Field private String publicationLink;
    @Field private String platform;
    @Field private String accessionId;
    @Field @NonEmbeddableField private Boolean fullPvalueSet;

    @Field private String initialSampleDescription;
    @Field private String replicateSampleDescription;

    @Field private Collection<String> authorsList;
    @Field private Collection<String> ancestralGroups;
    @Field private Collection<String> countriesOfOrigin;
    @Field private Collection<String> countriesOfRecruitment;
    @Field private Collection<Integer> numberOfIndividuals;

    @Field("numberOfIndividualsInitial")
    private Integer numberOfIndividualsInitial;
    @Field("numberOfIndividualsReplication")
    private Integer numberOfIndividualsReplication;
    @Field("discovery-sample-ancestry")
    private Collection<String> discoverySampleAncestry;
    @Field("replication-sample-ancestry")
    private Collection<String> replicationSampleAncestry;

    @Field private Collection<String> additionalAncestryDescription;
    @Field private Collection<String> ancestryLinks;
    @Field private Collection<String> genotypingTechnologies;
    @Field private String studyDesignComment;
    @Field @NonEmbeddableField private Boolean agreedToCc0;
    @Field @NonEmbeddableField private String cohort;
    @Field @NonEmbeddableField private String ftpLink;
    @Field @NonEmbeddableField private Boolean gxe;

    @Field @NonEmbeddableField private int associationCount;

    // embedded Association info
    @Field("association_qualifier") private Collection<String> qualifiers;
    @Field("association_rsId") private Collection<String> rsIds;
    @Field("association_strongestAllele") private Collection<String> strongestAlleles;
    @Field("association_context") private Collection<String> contexts;
    @Field("association_regions") private Collection<String> regions;
    @Field("association_entrezMappedGenes") private Collection<String> entrezMappedGenes;
    @Field("association_entrezMappedGeneLinks") private Collection<String> entrezMappedGeneLinks;
    @Field("association_ensemblMappedGenes") private Collection<String> ensemblMappedGenes;
    @Field("association_ensemblMappedGeneLinks") private Collection<String> ensemblMappedGeneLinks;
    @Field("association_reportedGene") private Collection<String> reportedGenes;
    @Field("association_reportedGeneLinks") private Collection<String> reportedGeneLinks;
    @Field("association_chromosomeName") private Collection<String> chromosomeNames;
    @Field("association_chromosomePosition") private Collection<Integer> chromosomePositions;
    @Field("association_last_modified") private Collection<String> lastModifiedDates;
    @Field("association_positionLinks") private Collection<String> positionLinks;

    //    @Field("association_locusDescription") private Collection<String> locusDescriptions;
    //    @Field("association_merged") private Long merged;

    // embedded DiseaseTrait info
    @Field("traitName") private Collection<String> traitNames;

    // embedded EfoTrait info
    @Field("mappedLabel") private Collection<String> mappedLabels;
    @Field("mappedUri") private Collection<String> mappedUris;

    // embedded background EfoTrait info
    @Field("mappedBkgLabel")
    private Collection<String> mappedBkgLabels;
    @Field("mappedBkgUri")
    private Collection<String> mappedBkgUris;





    public StudyDocument(Study study) {
        super(study);
        this.pubmedId = study.getPublicationId().getPubmedId();
        this.title = study.getPublicationId().getTitle();
        this.author = study.getPublicationId().getFirstAuthor().getFullname();
        this.authorAscii = study.getPublicationId().getFirstAuthor().getFullnameStandard();
        this.orcid = study.getPublicationId().getFirstAuthor().getOrcid();
        this.publication = study.getPublicationId().getPublication();
        this.accessionId = study.getAccessionId();
        this.fullPvalueSet = study.getFullPvalueSet();
        this.agreedToCc0 = study.isAgreedToCc0();
        this.cohort = getCohort(study);
        this.ftpLink = computeFtpLink(study);
        this.gxe = study.getGxe();

        this.initialSampleDescription = study.getInitialSampleSize();
        this.replicateSampleDescription = study.getReplicateSampleSize();

        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
//        df.setTimeZone(TimeZone.getTimeZone("UTC"));
        if (study.getPublicationId().getPublicationDate() != null) {
            this.publicationDate = df.format(study.getPublicationId().getPublicationDate());
        }
        if (study.getHousekeeping().getCatalogPublishDate() != null) {
            this.catalogPublishDate = df.format(study.getHousekeeping().getCatalogPublishDate());
        }

        String year;
        if (study.getPublicationId().getPublicationDate() != null) {
            Calendar studyCal = Calendar.getInstance();
            studyCal.setTime(study.getPublicationId().getPublicationDate());
            year = String.valueOf(studyCal.get(Calendar.YEAR));
        }
        else {
            year = "N/A";
        }
        this.publicationLink = author.concat("|").concat(year).concat("|").concat(pubmedId);

        this.authorsList = new LinkedHashSet<>();
        embedAuthors(study);
        this.platform = embedPlatformField(study);
        this.ancestralGroups = new LinkedHashSet<>();
        this.countriesOfOrigin = new LinkedHashSet<>();
        this.countriesOfRecruitment = new LinkedHashSet<>();
        this.numberOfIndividuals = new LinkedHashSet<>();
        this.additionalAncestryDescription = new LinkedHashSet<>();
        this.ancestryLinks = new LinkedHashSet<>();
        embedAncestryData(study);

        this.genotypingTechnologies = new LinkedHashSet<>();
        embedGenotypingTechnologiesData(study);
        this.studyDesignComment = study.getStudyDesignComment();

        this.qualifiers = new LinkedHashSet<>();
        this.rsIds = new LinkedHashSet<>();
        this.strongestAlleles = new LinkedHashSet<>();
        this.contexts = new LinkedHashSet<>();
        this.regions = new LinkedHashSet<>();
        this.entrezMappedGenes = new LinkedHashSet<>();
        this.entrezMappedGeneLinks = new LinkedHashSet<>();
        this.ensemblMappedGenes = new LinkedHashSet<>();
        this.ensemblMappedGeneLinks = new LinkedHashSet<>();
        this.reportedGenes = new LinkedHashSet<>();
        this.reportedGeneLinks = new LinkedHashSet<>();
        this.chromosomeNames = new LinkedHashSet<>();
        this.chromosomePositions = new LinkedHashSet<>();
        this.positionLinks = new LinkedHashSet<>();
        this.lastModifiedDates = new LinkedHashSet<>();
        //        this.locusDescriptions = new LinkedHashSet<>();

        this.traitNames = new LinkedHashSet<>();

        this.mappedLabels = new LinkedHashSet<>();
        this.mappedUris = new LinkedHashSet<>();

        this.mappedBkgLabels = new LinkedHashSet<>();
        this.mappedBkgUris = new LinkedHashSet<>();

        computeInitialAndReplicationNUmber(study);
        extractDiscoveryAndReplicationSampleAncestryList();
    }

    public String getPubmedId() {
        return pubmedId;
    }

    public String getTitle() {
        return title;
    }

    public String getAuthor() { return author; }

    public String getOrcid() { return orcid; }

    public String getAuthorAscii() { return authorAscii; }

    public Collection<String> getAuthorsList() { return authorsList; }

    public String getPublication() {
        return publication;
    }

    public String getPublicationDate() {
        return publicationDate;
    }

    public String getCatalogPublishDate() {
        return catalogPublishDate;
    }

    public String getPublicationLink() {
        return publicationLink;
    }

    public String getInitialSampleDescription() {
        return initialSampleDescription;
    }

    public String getReplicateSampleDescription() {
        return replicateSampleDescription;
    }

    public int getAssociationCount() {
        return associationCount;
    }

    public void setAssociationCount(int associationCount) {
        this.associationCount = associationCount;
    }

    public void addQualifier(String qualifier) {
        this.qualifiers.add(qualifier);
    }

    public void addRsId(String rsId) {
        this.rsIds.add(rsId);
    }

    public void addStrongestAllele(String strongestAllele) {
        this.strongestAlleles.add(strongestAllele);
    }

    public void addContext(String context) {
        this.contexts.add(context);
    }

    public void addRegion(Collection<String> regions) {
        this.regions.addAll(regions);
    }

    public void addEntrezMappedGenes(Collection<String> mappedGenes) {
        this.entrezMappedGenes.addAll(mappedGenes);
    }

    public void addEntrezMappedGeneLinks(Collection<String> mappedGeneLinks) {
        this.entrezMappedGeneLinks.addAll(mappedGeneLinks);
    }

    public void addEnsemblMappedGenes(Collection<String> mappedGenes) {
        this.ensemblMappedGenes.addAll(mappedGenes);
    }

    public void addEnsemblMappedGeneLinks(Collection<String> mappedGeneLinks) {
        this.ensemblMappedGeneLinks.addAll(mappedGeneLinks);
    }


    public void addReportedGenes(Collection<String> reportedGenes) {
        this.reportedGenes.addAll(reportedGenes);
    }

    public void addReportedGeneLinks(Collection<String> reportedGeneLinks) {
        this.reportedGeneLinks.addAll(reportedGeneLinks);
    }

    public void addChromosomeNames(Collection<String> chromosomeNames) {
        this.chromosomeNames.addAll(chromosomeNames);
    }

    public void addChromosomePositions(Collection<Integer> chromosomePositions) {
        this.chromosomePositions.addAll(chromosomePositions);
    }

    public void addPositionLinks(Collection<String> positionLinks) {
        this.positionLinks.addAll(positionLinks);
    }

    public void addLastModifiedDates(Collection<String> lastModifiedDates) {
        this.lastModifiedDates.addAll(lastModifiedDates);
    }

    //    public void addMerged(Long merged){
    //        this.merged = merged;
    //    }

    public void addTraitName(String traitName) {
        this.traitNames.add(traitName);
    }

    public void addMappedLabel(String mappedLabel) {
        this.mappedLabels.add(mappedLabel);
    }

    public void addMappedUri(String mappedUri) {
        this.mappedUris.add(mappedUri);
    }

    public void addMappedBkgUri(String mappedBkgUri) {
        this.mappedBkgUris.add(mappedBkgUri);
    }

    public void addMappedBkgLabel(String mappedBkgLabel) {
        this.mappedBkgLabels.add(mappedBkgLabel);
    }

    public Collection<String> getAncestralGroups() {
        return ancestralGroups;
    }

    public Collection<String> getCountriesOfRecruitment() {
        return countriesOfRecruitment;
    }


    public Collection<Integer> getNumberOfIndividuals() {
        return numberOfIndividuals;
    }


    public Collection<String> getGenotypingTechnologies() {return genotypingTechnologies; }


    private void embedGenotypingTechnologiesData(Study study){
        Collection<GenotypingTechnology> genotypingTechnologiesCollection = study.getGenotypingTechnologies();
        for (GenotypingTechnology genotypingTechnology: genotypingTechnologiesCollection) {
            genotypingTechnologies.add(genotypingTechnology.getGenotypingTechnology());
        }
    }


    private void embedAuthors(Study study) {
        //Collection<Author> authors = study.getPublicationId().getAuthors();
        Collection<PublicationAuthors> authors = study.getPublicationId().getPublicationAuthors();
        for (PublicationAuthors author : authors) {
            String authorLink = author.getAuthor().getFullname().concat(" | ").concat(author.getAuthor().getFullnameStandard()).concat(" | ").concat(author.getSort().toString());
            if (author.getAuthor().getOrcid() != null) {
                authorLink = authorLink.concat(" | ").concat(author.getAuthor().getOrcid());
            }
            authorsList.add(authorLink);
        }

    }


    public Collection<String> getAncestryLinks() {
        return ancestryLinks;
    }

    private void embedAncestryData(Study study) {
        study.getAncestries().forEach(
                ancestry -> {
                    String ancestryLink = "";

                    String type = ancestry.getType();

                    ancestryLink = type;

                    String coo = null;

                    if(ancestry.getCountryOfOrigin() != null && !ancestry.getCountryOfOrigin().isEmpty()) {
                        for(Country c : ancestry.getCountryOfOrigin()){
                            if (coo == null){
                                coo = c.getCountryName();
                            }
                            else {
                                coo = coo.concat(", ").concat(c.getCountryName());
                            }
                        }
                    }
                    else {
                        coo = "NR";
                    }

                    ancestryLink = ancestryLink.concat("|").concat(coo);


                    String cor = null;

                    if (ancestry.getCountryOfRecruitment() != null && !ancestry.getCountryOfRecruitment().isEmpty()) {
                        for(Country c : ancestry.getCountryOfRecruitment()){
                            if (cor == null){
                                cor = c.getCountryName();
                            }
                            else {
                                cor = cor.concat(", ").concat(c.getCountryName());
                            }
                        }                    }
                    else {
                        cor = "NR";
                    }
                    countriesOfRecruitment.add(cor);
                    ancestryLink = ancestryLink.concat("|").concat(cor);

                    String ances = null;

                    if (ancestry.getAncestralGroups() != null && !ancestry.getAncestralGroups().isEmpty()) {
                        for(AncestralGroup a : ancestry.getAncestralGroups()){
                            if (ances == null){
                                ances = a.getAncestralGroup();
                            }
                            else {
                                ances = ances.concat(", ").concat(a.getAncestralGroup());
                            }
                        }                    }
                    else{
                        ances = "NR";
                    }

                    ancestralGroups.add(ances);
                    ancestryLink = ancestryLink.concat("|").concat(ances);

                    String noInds = "";

                    if (ancestry.getNumberOfIndividuals() != null) {
                        numberOfIndividuals.add(ancestry.getNumberOfIndividuals());
                        noInds = String.valueOf(ancestry.getNumberOfIndividuals());
                    }
                    else{
                        noInds = "NA";
                    }

                    ancestryLink = ancestryLink.concat("|").concat(noInds);

                    String description = "";

                    if(ancestry.getDescription() != null && ancestry.getDescription().trim() != ""){
                        additionalAncestryDescription.add(ancestry.getDescription());
                        description = ancestry.getDescription();
                    }
                    else {
                        description = "NA";
                    }

                    ancestryLink = ancestryLink.concat("|").concat(description);

                    ancestryLinks.add(ancestryLink);

                }
        );
    }

    public String getPlatform() {
        return platform;
    }


    //    public void addLocusDescription(String locusDescription){
    //        this.locusDescriptions.add(locusDescription);
    //    }

    private String embedPlatformField(Study study) {
        String platform = "";
        List<String> manufacturers = new ArrayList<>();
        study.getPlatforms().forEach(
                p -> {
                    String manufacturer = p.getManufacturer();
                    manufacturers.add(manufacturer);
                }
        );


        if (manufacturers.size() != 0) {
            Collections.sort(manufacturers);
            for (String m : manufacturers) {
                if (platform.equals("")) {
                    platform = m;
                }
                else {
                    platform = platform.concat(", ").concat(m);
                }
            }
        }
        else {
            platform = "NR";
        }


        platform = platform.concat(" [");

        if (study.getQualifier() != null) {
            platform = platform.concat(study.getQualifier()).concat(" ");
        }

        if (study.getSnpCount() != null) {
            platform = platform.concat(study.getSnpCount().toString()).concat("]");
        }
        else if (study.getSnpCount() == null && manufacturers.size() != 0 && study.getStudyDesignComment() != null){
            platform = platform.concat(study.getStudyDesignComment()).concat("]");
        }
        else {
            platform = platform.concat("NR]");
        }

        if (study.getImputed()) {
            platform = platform.concat(" (imputed)");
        }

        if (manufacturers.size() == 0 && study.getSnpCount() == null && study.getStudyDesignComment() != null) {
            platform = study.getStudyDesignComment();
        }


        return platform;
    }




    public Collection<String> getAdditionalAncestryDescription() {
        return additionalAncestryDescription;
    }

    public String getAccessionId() {
        return accessionId;
    }

    public Boolean getFullPvalueSet() { return fullPvalueSet; }

    public String getStudyDesignComment() { return studyDesignComment; }

    public Boolean getAgreedToCc0() {
        return agreedToCc0;
    }

    public String getCohort() {
        return cohort;
    }

    public String getFtpLink() {
        return ftpLink;
    }

    public Boolean getGxe() {
        return gxe;
    }

    private void extractDiscoveryAndReplicationSampleAncestryList() {
        discoverySampleAncestry = this.ancestryLinks.stream()
                .filter((ancestryText) -> ancestryText.startsWith("initial"))
                .map((text) -> {
                    StringBuilder initialSampleText = new StringBuilder();
                    String[] freetext = text.split("\\|");
                    initialSampleText.append(freetext[4]);
                    initialSampleText.append(" ");
                    initialSampleText.append(freetext[3]);
                    return initialSampleText.toString();
                }).collect(Collectors.toList());

        replicationSampleAncestry = this.ancestryLinks.stream()
                .filter((ancestryText) -> ancestryText.startsWith("replication"))
                .map((text) -> {
                    StringBuilder replicateSampleText = new StringBuilder();
                    String[] freetext = text.split("\\|");
                    replicateSampleText.append(freetext[4]);
                    replicateSampleText.append(" ");
                    replicateSampleText.append(freetext[3]);
                    return replicateSampleText.toString();
                }).collect(Collectors.toList());

    }

    private String computeFtpLink(Study study) {
        if (study.getFullPvalueSet()) {
            String accId = study.getAccessionId().substring(study.getAccessionId().indexOf("GCST")+4);
            int gsctNum = Integer.parseInt(accId);
            int lowerRange = (int) (Math.floor((gsctNum-1)/1000))*1000+1;
            int upperRange = (int) (Math.floor((gsctNum-1)/1000)+1)*1000;
            String range = "GCST"+ StringUtils.leftPad(String.valueOf(lowerRange),6,"0")
                    +"-GCST"+StringUtils.leftPad(String.valueOf(upperRange),6,"0");
            return "http://ftp.ebi.ac.uk/pub/databases/gwas/summary_statistics/".concat(range).concat("/").concat(study.getAccessionId());
        }
        return null;
    }

    private String getCohort(Study study) {
        StudyExtension studyExtension = study.getStudyExtension();
        if (studyExtension != null) return studyExtension.getCohort();
        return null;
    }

    private void computeInitialAndReplicationNUmber(Study study) {
        AtomicInteger noOfInitialIndividuals = new AtomicInteger();;
        AtomicInteger noOfReplicationIndividuals = new AtomicInteger();;
        study.getAncestries().forEach(
                ancestry -> {
                    String type = ancestry.getType();
                    if(type.equals("initial")) {
                        if(ancestry.getNumberOfIndividuals() != null)
                        noOfInitialIndividuals.addAndGet(ancestry.getNumberOfIndividuals());
                    }
                    if(type.equals("replication")) {
                        if(ancestry.getNumberOfIndividuals() != null)
                        noOfReplicationIndividuals.addAndGet(ancestry.getNumberOfIndividuals());
                    }
                });
        numberOfIndividualsInitial = noOfInitialIndividuals.intValue();
        numberOfIndividualsReplication = noOfReplicationIndividuals.intValue();
    }
}
