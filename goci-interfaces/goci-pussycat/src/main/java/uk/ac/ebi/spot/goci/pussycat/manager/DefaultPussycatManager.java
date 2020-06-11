package uk.ac.ebi.spot.goci.pussycat.manager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import uk.ac.ebi.spot.goci.pussycat.exception.PussycatSessionNotReadyException;
import uk.ac.ebi.spot.goci.pussycat.renderlet.RenderletNexus;
import uk.ac.ebi.spot.goci.pussycat.renderlet.RenderletNexusFactory;
import uk.ac.ebi.spot.goci.pussycat.session.PussycatSession;

import javax.servlet.http.HttpSession;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The default implementation of a {@link uk.ac.ebi.spot.goci.pussycat.manager.PussycatManager}.  This implementation
 * uses a single, preconfigured {@link PussycatSession} that is used to ensure data is only loaded once, and all
 * requests go through this single pussycat session.  This manager should also be prewired with a {@link
 * uk.ac.ebi.spot.goci.pussycat.renderlet.RenderletNexusFactory} to construct any new, required instances of a renderlet
 * nexus.  The normal strategy is to generate one renderlet nexus per HTTP session, but to reuse one pussycat session
 * across all sessions.  This way, the data only needs to be obtained once but multiple different renderings of that
 * data can occur.
 *
 * @author Tony Burdett
 * @date 04/06/14
 */
@Component
@Qualifier("pussycatManager")
public class DefaultPussycatManager implements PussycatManager {
    private RenderletNexusFactory nexusFactory;

    private Set<PussycatSession> sessions;
    private Map<String, PussycatSession> sessionMap;

    private Map<String, RenderletNexus> nexusMap = new HashMap<String, RenderletNexus>();

    private Logger log = LoggerFactory.getLogger(getClass());

    public DefaultPussycatManager() {
        this.sessions = new HashSet<PussycatSession>();
        this.sessionMap = new HashMap<String, PussycatSession>();
        this.nexusMap = new HashMap<String, RenderletNexus>();
    }

    protected Logger getLog() {
        return log;
    }

    public RenderletNexusFactory getNexusFactory() {
        return nexusFactory;
    }

    @Autowired
    public void setNexusFactory(RenderletNexusFactory nexusFactory) {
        this.nexusFactory = nexusFactory;
    }

    public Set<PussycatSession> getPussycatSessions() {
        return sessions;
    }

    @Autowired
    @Qualifier("cachingSession")
    public void setPussycatSession(PussycatSession session) {
        this.sessions = Collections.singleton(session);
    }

    public boolean hasAvailablePussycatSession(HttpSession session) {
        return sessionMap.containsKey(session.getId());
    }

    public PussycatSession getPussycatSession(HttpSession session) {
        return sessionMap.get(session.getId());
    }

    public PussycatSession bindPussycatSession(HttpSession session, PussycatSession pussycatSession) {
        if (!sessions.contains(pussycatSession)) {
            sessions.add(pussycatSession);
        }
        sessionMap.put(session.getId(), pussycatSession);
        return pussycatSession;
    }

    @Override public boolean hasAvailableRenderletNexus(HttpSession session) {
        return nexusMap.containsKey(session.getId());
    }

    @Override public RenderletNexus getRenderletNexus(HttpSession session) {
        return nexusMap.get(session.getId());
    }

    @Override public RenderletNexus bindRenderletNexus(HttpSession session, RenderletNexus renderletNexus) {
        nexusMap.put(session.getId(), renderletNexus);
        return renderletNexus;
    }

    @Override public boolean unbindResources(HttpSession session) {
        boolean sessionUnbound = false;
        boolean nexusUnbound = false;
        if (sessionMap.containsKey(session.getId())) {
            PussycatSession ps = sessionMap.remove(session.getId());
            getLog().debug("PussycatSession '" + ps.getSessionID() + "' is no longer bound to " +
                                   "HttpSession '" + session.getId() + "'");
            sessionUnbound = true;
        }
        else {
            getLog().debug("Cannot unbind PussycatSession for HttpSession '" + session.getId() + "' " +
                                   "- no linked PussycatSession resource");
        }
        if (nexusMap.containsKey(session.getId())) {
            RenderletNexus rn = nexusMap.remove(session.getId());
            getLog().debug("RenderletNexus '" + rn + "' is no longer bound to " +
                                   "HttpSession '" + session.getId() + "'");
            nexusUnbound = true;
        }
        else {
            getLog().debug("Cannot unbind RenderletNexus for HttpSession '" + session.getId() + "' " +
                                   "- no linked RenderletNexus resource");
        }
        return sessionUnbound && nexusUnbound;
    }

    public PussycatSession createPussycatSession() {
        throw new UnsupportedOperationException(
                "This implementation reuses a single, prewired Pussycat Session - new ones cannot be created");
    }

    @Override public RenderletNexus createRenderletNexus(PussycatSession session)
            throws PussycatSessionNotReadyException {
        return getNexusFactory().createRenderletNexus(session);
    }
}
