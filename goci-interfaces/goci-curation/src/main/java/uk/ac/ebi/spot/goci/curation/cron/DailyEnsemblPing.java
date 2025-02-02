package uk.ac.ebi.spot.goci.curation.cron;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import uk.ac.ebi.spot.goci.curation.model.Ping;
import uk.ac.ebi.spot.goci.curation.service.mail.MailService;

/**
 * Created by emma on 08/01/2016.
 *
 * @author emma
 *         <p>
 *         Daily check to see if Ensembl API is alive.
 */
@Component
public class DailyEnsemblPing {

    private MailService mailService;

    private final Logger log = LoggerFactory.getLogger(getClass());

    protected Logger getLog() {
        return log;
    }

    @Autowired
    public DailyEnsemblPing(MailService mailService) {
        this.mailService = mailService;
    }

    @Scheduled(cron = "0 0 7 * * *")
    public void pingEnsembl() {
        RestTemplate restTemplate = new RestTemplate();
        String url = "http://rest.ensembl.org/info/ping?content-type=application/json";
        Ping ping = new Ping();

        try {
            ping = restTemplate.getForObject(url, Ping.class);
            Integer num = ping.getPing();
            getLog().info("Pinging Ensembl: " + url);

            if (num != 1) {
                mailService.sendEnsemblPingFailureMail();
                getLog().error("Pinging Ensembl returned " + num);
            }
            else {
                getLog().info("Pinging Ensembl returned " + num);
            }
        }

        catch (Exception e) {
            getLog().error("Pinging Ensembl failed", e);
            mailService.sendEnsemblPingFailureMail();
        }

    }
}
