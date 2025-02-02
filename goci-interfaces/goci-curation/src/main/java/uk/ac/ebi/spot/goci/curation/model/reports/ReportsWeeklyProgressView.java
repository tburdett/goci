package uk.ac.ebi.spot.goci.curation.model.reports;

import java.util.Date;
import java.util.Set;

/**
 * Created by emma on 09/06/2016.
 *
 * @author emma
 *         <p>
 *         Model class to represent weekly curation progress
 */
public class ReportsWeeklyProgressView {

    private Date weekDate;

    private Set<Long> studiesCreated;

    private Set<Long> studiesLevel1Completed;

    private Set<Long> studiesLevel2Completed;

    private Set<Long> studiesPublished;

    private Set<String> publicationsCreated;

    private Set<String> publicationsLevel1Completed;

    private Set<String> publicationsLevel2Completed;

    private Set<String> publicationsPublished;

    public ReportsWeeklyProgressView(Date weekDate) {
        this.weekDate = weekDate;
    }

    public Date getWeekDate() {
        return weekDate;
    }

    public void setWeekDate(Date weekDate) {
        this.weekDate = weekDate;
    }

    public Set<Long> getStudiesCreated() {
        return studiesCreated;
    }

    public void setStudiesCreated(Set<Long> studiesCreated) {
        this.studiesCreated = studiesCreated;
    }

    public Set<Long> getStudiesLevel1Completed() {
        return studiesLevel1Completed;
    }

    public void setStudiesLevel1Completed(Set<Long> studiesLevel1Completed) {
        this.studiesLevel1Completed = studiesLevel1Completed;
    }

    public Set<Long> getStudiesLevel2Completed() {
        return studiesLevel2Completed;
    }

    public void setStudiesLevel2Completed(Set<Long> studiesLevel2Completed) {
        this.studiesLevel2Completed = studiesLevel2Completed;
    }

    public Set<Long> getStudiesPublished() {
        return studiesPublished;
    }

    public void setStudiesPublished(Set<Long> studiesPublished) {
        this.studiesPublished = studiesPublished;
    }

    public Set<String> getPublicationsCreated() { return publicationsCreated; }

    public void setPublicationsCreated(Set<String> publicationsCreated) {
        this.publicationsCreated = publicationsCreated; }

    public Set<String> getPublicationsPublished() { return publicationsPublished; }

    public void setPublicationsPublished(Set<String> publicationsPublished) {
        this.publicationsPublished = publicationsPublished; }

    public Set<String> getPublicationsLevel1Completed() { return publicationsLevel1Completed; }

    public void setPublicationsLevel1Completed(Set<String> publicationsLevel1Completed) {
        this.publicationsLevel1Completed = publicationsLevel1Completed; }

    public Set<String> getPublicationsLevel2Completed() { return publicationsLevel2Completed; }

    public void setPublicationsLevel2Completed(Set<String> publicationsLevel2Completed) {
        this.publicationsLevel2Completed = publicationsLevel2Completed; }
}