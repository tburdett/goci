package uk.ac.ebi.spot.goci.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Created by emma on 22/04/2016.
 *
 * @author emma
 *         <p>
 *         Model to process response from: http://rest.ensembl.org/lookup/symbol/homo_sapiens/SFRP1?content-type=application/json
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GeneLookupJson {

    private String object_type;

    public String getObject_type() {
        return object_type;
    }

    public void setObject_type(String object_type) {
        this.object_type = object_type;
    }
}
