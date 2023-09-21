package com.graphhopper.jsprit.core.problem.solution.route.activity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import com.graphhopper.jsprit.core.problem.misc.JobInsertionContext;


public class TimeWindowsOverlapImpl implements TimeWindows {

    private List<TimeWindow> includedTimeWindows = new ArrayList<TimeWindow>();
    private List<TimeWindow> excludedTimeWindows = new ArrayList<TimeWindow>();

    @Override
    public void add(TimeWindow timeWindow) {
        if (timeWindow == null) {
            throw new IllegalArgumentException("The time window must not be null.");
        }

        includedTimeWindows.add(timeWindow);

        // Keep collection sorted by start time - needed by getTimeWindows()
        Collections.sort(includedTimeWindows);
    }

    public void addExcludedTimeWindow(TimeWindow timeWindow) {
        if (timeWindow == null) {
            throw new IllegalArgumentException("The time window must not be null.");
        }
        excludedTimeWindows.add(timeWindow);

        // Keep collection sorted by start time - needed by getTimeWindows()
        Collections.sort(excludedTimeWindows);
    }

    public void addIncludedTimeWindow(TimeWindow timeWindow) {
        // Synonym for the sake of symmetry
        add(timeWindow);
    }

    @Override
    public Collection<TimeWindow> getTimeWindows() {
        return Collections.unmodifiableCollection(includedTimeWindows);
    }

    @Override
    public Collection<TimeWindow> getTimeWindows(JobInsertionContext insertionContext) {
        // The easy case: no exclusions, no performance loss.
        if (excludedTimeWindows.isEmpty()) {
            return getTimeWindows();
        }

        // Second easy case: no applicable exclusions.
        List<TimeWindow> applicableExclusions = new ArrayList<TimeWindow>(excludedTimeWindows.size());
        for (TimeWindow excludedTw : excludedTimeWindows) {
            if (excludedTw.isApplicable(insertionContext)) {
                applicableExclusions.add(excludedTw);
            }
        }
        if (applicableExclusions.isEmpty()) {
            return getTimeWindows();
        }

        // First: filter included TW that are applicable
        List<TimeWindow> result = new ArrayList<TimeWindow>(includedTimeWindows.size());
        for(TimeWindow includedTw : includedTimeWindows) {
            if (!includedTw.isApplicable(insertionContext)) {
                continue;
            }
            result.add(includedTw);
        }
        if (result.isEmpty()) {
            // No applicable TW means the job has no time insertion constraints. So just use the "infinite" TW.
            result.add(defaultTimeWindow);
        }

        // Then remove the exclusions
        for(TimeWindow excludedTw : applicableExclusions) {
            result = cutTimeWindowsIfNeeded(excludedTw, result);
        }
        // Note that the result can be empty! This is normal: we can exclude everything and make the job impossible to insert.

        // And we are done!
        return Collections.unmodifiableCollection(result);
    }

    protected List<TimeWindow> cutTimeWindowsIfNeeded(TimeWindow excludedTw, List<TimeWindow> currentResult) {
        List<TimeWindow> newResult = new ArrayList<TimeWindow>();
        for (TimeWindow includedTw : currentResult) {
            if (excludedTw.getStart() >= includedTw.getStart() && excludedTw.getEnd() <= excludedTw.getEnd()) {
                // Inclusion           [            ]
                // Exclusion             [      ]
                newResult.add(TimeWindow.newInstance(includedTw.getStart(), excludedTw.getEnd()));
                newResult.add(TimeWindow.newInstance(includedTw.getEnd(), excludedTw.getEnd()));
            }
            else if (excludedTw.getStart() < includedTw.getStart() && excludedTw.getEnd() < excludedTw.getEnd() && excludedTw.getEnd() > includedTw.getStart()) {
                // Inclusion           [            ]
                // Exclusion        [      ]
                newResult.add(TimeWindow.newInstance(excludedTw.getStart(), includedTw.getEnd()));
            }
            else if (excludedTw.getStart() > includedTw.getStart() && excludedTw.getEnd() > includedTw.getEnd() && excludedTw.getStart() < includedTw.getEnd()) {
                // Inclusion           [            ]
                // Exclusion                      [      ]
                newResult.add(TimeWindow.newInstance(excludedTw.getStart(), includedTw.getStart()));
            }
            else if (excludedTw.getStart() < includedTw.getStart() && excludedTw.getEnd() > includedTw.getEnd()) {
                // Inclusion           [            ]
                // Exclusion        [                  ]
                // => nothing to add
            }
            else {
                newResult.add(includedTw);
            }
        }
        return newResult;
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer(includedTimeWindows.size() * 60);
        sb.append("Included:\n");
        for (TimeWindow tw : includedTimeWindows) {
            sb.append("[timeWindow=").append(tw).append("]");
        }
        sb.append("Excluded:\n");
        for (TimeWindow tw : excludedTimeWindows) {
            sb.append("[timeWindow=").append(tw).append("]");
        }
        return sb.toString();
    }
}
