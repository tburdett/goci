package uk.ac.ebi.spot.goci.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class DepositionSampleDto {
    private String studyTag;
    private String stage;
    private Integer size;
    private Integer cases;
    private Integer controls;
    private String sampleDescription;
    private String ancestryCategory;
    private String ancestry;
    private String ancestryDescription;
    private String countryRecruitement;
}
