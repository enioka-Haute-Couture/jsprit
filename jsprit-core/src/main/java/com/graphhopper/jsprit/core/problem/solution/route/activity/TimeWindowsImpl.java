/*
 * Licensed to GraphHopper GmbH under one or more contributor
 * license agreements. See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 *
 * GraphHopper GmbH licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.graphhopper.jsprit.core.problem.solution.route.activity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import com.graphhopper.jsprit.core.problem.misc.JobInsertionContext;

/**
 * Created by schroeder on 26/05/15.
 */
public class TimeWindowsImpl implements TimeWindows {

    private Collection<TimeWindow> timeWindows = new ArrayList<TimeWindow>();

    @Override
    public void add(TimeWindow timeWindow) {
        // TODO : avoid overlaping for same type of TW ?
        /*
        for(TimeWindow tw : timeWindows){
            if(timeWindow.getStart() > tw.getStart() && timeWindow.getStart() < tw.getEnd()){
                throw new IllegalArgumentException("time-windows cannot overlap each other. overlap: " + tw + ", " + timeWindow);
            }
            if(timeWindow.getEnd() > tw.getStart() && timeWindow.getEnd() < tw.getEnd()){
                throw new IllegalArgumentException("time-windows cannot overlap each other. overlap: " + tw + ", " + timeWindow);
            }
            if(timeWindow.getStart() <= tw.getStart() && timeWindow.getEnd() >= tw.getEnd()){
                throw new IllegalArgumentException("time-windows cannot overlap each other. overlap: " + tw + ", " + timeWindow);
            }
        }
        */
        timeWindows.add(timeWindow);
    }

    @Override
    public Collection<TimeWindow> getTimeWindows() {
        return Collections.unmodifiableCollection(timeWindows);
    }

    @Override
    public Collection<TimeWindow> getTimeWindows(JobInsertionContext insertionContext) {
        // Reorder by start time
        ArrayList<TimeWindow> twList = new ArrayList<TimeWindow>(timeWindows);
        Collections.sort(twList);

        ArrayList<TimeWindow> result = new ArrayList<TimeWindow>();
        int startIdx = 1;
        boolean hasExcludingTW = false;
        boolean hasNonExcludingTW = false;
        for (TimeWindow timeWindow : twList) {
            if (!timeWindow.isApplicable(insertionContext)) {
                continue;
            }
            for (int i = startIdx; i < twList.size(); i++) {
                if (!timeWindow.isApplicable(insertionContext) || timeWindow == twList.get(i)) {
                    continue;
                }

                // Check if overlap
                if (twList.get(i).getStart() < timeWindow.getEnd()) {
                    // Which TW intersect
                    result.add(TimeWindow.newInstance(
                            timeWindow.isExcluding() ? timeWindow.getStart() : twList.get(i).getStart(),
                            timeWindow.isExcluding() ? timeWindow.getEnd() : twList.get(i).getEnd()));
                }
            }
            startIdx++;
            hasExcludingTW |= timeWindow.isExcluding();
            hasNonExcludingTW |= !timeWindow.isExcluding();
        }
        if (result.isEmpty()) {
            if (hasExcludingTW ^ hasNonExcludingTW) {
                return twList;
            } else {
                result.add(TimeWindow.newInstance(0.0, Double.MAX_VALUE));
            }
        }
        return result;
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer(timeWindows.size() * 60);
        for (TimeWindow tw : timeWindows) {
            sb.append("[timeWindow=").append(tw).append("]");
        }
        return sb.toString();
    }
}
