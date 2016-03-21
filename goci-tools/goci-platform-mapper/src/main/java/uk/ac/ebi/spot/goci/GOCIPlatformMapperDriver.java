package uk.ac.ebi.spot.goci;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import uk.ac.ebi.spot.goci.service.PlatformMappingService;

/**
 * Created by dwelter on 08/03/16.
 */
@SpringBootApplication
public class GOCIPlatformMapperDriver {

    @Autowired
    private PlatformMappingService platformMappingService;


    public static void main(String[] args) {
        System.out.println("Starting platform mapping application...");
        ApplicationContext ctx = SpringApplication.run(GOCIPlatformMapperDriver.class, args);
        System.out.println("Application executed successfully!");
        SpringApplication.exit(ctx);
    }

    @Bean CommandLineRunner run() {
        return strings -> {
            System.out.println("About to map all database values");
            platformMappingService.mapAllValues();
            System.out.println("Mapping complete");
        };
    }
}
