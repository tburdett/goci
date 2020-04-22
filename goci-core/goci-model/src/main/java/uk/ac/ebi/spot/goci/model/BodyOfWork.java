package uk.ac.ebi.spot.goci.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.joda.time.LocalDate;
import uk.ac.ebi.spot.goci.model.deposition.BodyOfWorkDto;

import javax.persistence.*;
import java.sql.Date;
import java.util.Set;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
public class BodyOfWork{
    @Id
    @GeneratedValue
    private Long id;
    @Column(name = "pub_id")
    private String publicationId;
    private String pubMedId;
    private String journal;
    private String title;
    private String firstAuthor;
//    private DepositionAuthor correspondingAuthor;
    @Column(name = "pub_date")
    private Date publicationDate;
//    private String status;
    private String doi;

    @ManyToMany
    @JoinTable(name = "unpublished_study_to_work", joinColumns = @JoinColumn(name = "work_id"), inverseJoinColumns =
    @JoinColumn(name = "study_id"))
    private Set<UnpublishedStudy> studies;

    public static BodyOfWork create(BodyOfWorkDto dto){
        BodyOfWork bodyOfWork = new BodyOfWork();
        bodyOfWork.setPublicationId(dto.getBodyOfWorkId());
        bodyOfWork.setPubMedId(dto.getPmids() != null ? dto.getPmids().get(0) : null);
        bodyOfWork.setJournal(dto.getJournal());
        bodyOfWork.setTitle(dto.getTitle());
        bodyOfWork.setFirstAuthor(dto.getFirstAuthor().getAuthorName());
        //bodyOfWork.setPublicationDate(dto.get);
        bodyOfWork.setDoi(dto.getDoi());
        return bodyOfWork;
    }
}