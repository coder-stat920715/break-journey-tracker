package com.souptik.breakjourney.simulator;

import com.souptik.breakjourney.service.TimeUtil;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Holds the virtual clock of each journey simulation. Without a simulation the real clock is used. */
@Component
public class SimulationClock {

    public static final class State {
        private volatile OffsetDateTime virtualTime;
        private volatile boolean running;
        private volatile boolean finished;

        public OffsetDateTime getVirtualTime() { return virtualTime; }
        public void setVirtualTime(OffsetDateTime virtualTime) { this.virtualTime = virtualTime; }
        public boolean isRunning() { return running; }
        public void setRunning(boolean running) { this.running = running; }
        public boolean isFinished() { return finished; }
        public void setFinished(boolean finished) { this.finished = finished; }
    }

    private final Map<Long, State> states = new ConcurrentHashMap<>();

    public State start(Long journeyId, OffsetDateTime startTime) {
        State state = new State();
        state.setVirtualTime(startTime);
        state.setRunning(true);
        states.put(journeyId, state);
        return state;
    }

    public Optional<State> find(Long journeyId) {
        return Optional.ofNullable(states.get(journeyId));
    }

    public Map<Long, State> all() {
        return states;
    }

    public void clear(Long journeyId) {
        states.remove(journeyId);
    }

    public boolean isSimulated(Long journeyId) {
        return states.containsKey(journeyId);
    }

    public boolean isRunning(Long journeyId) {
        State s = states.get(journeyId);
        return s != null && s.isRunning();
    }

    public OffsetDateTime now(Long journeyId) {
        State s = states.get(journeyId);
        return s != null ? s.getVirtualTime() : TimeUtil.nowIst();
    }
}
