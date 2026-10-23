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
package com.graphhopper.jsprit.core.algorithm.ruin;

import com.graphhopper.jsprit.core.problem.Location;
import com.graphhopper.jsprit.core.problem.driver.DriverImpl;
import com.graphhopper.jsprit.core.problem.VehicleRoutingProblem;
import com.graphhopper.jsprit.core.problem.job.Job;
import com.graphhopper.jsprit.core.problem.job.Service;
import com.graphhopper.jsprit.core.problem.job.Shipment;
import com.graphhopper.jsprit.core.problem.solution.route.VehicleRoute;
import com.graphhopper.jsprit.core.problem.solution.route.activity.TourActivity;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleImpl;
import com.graphhopper.jsprit.core.util.Coordinate;
import com.graphhopper.jsprit.core.util.NoiseMaker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Created by schroeder on 30/01/15.
 */
@DisplayName("Ruin Worst Test")
class RuinWorstTest {

    @Test
    @DisplayName("It Should Remove Correct Number")
    void itShouldRemoveCorrectNumber() {
        Service s1 = Service.Builder.newInstance("s1").setLocation(Location.Builder.newInstance().setCoordinate(Coordinate.newInstance(1, 1)).build()).build();
        Service s2 = Service.Builder.newInstance("s2").setLocation(Location.Builder.newInstance().setCoordinate(Coordinate.newInstance(3, 1)).build()).build();
        Service s3 = Service.Builder.newInstance("s3").setLocation(Location.Builder.newInstance().setCoordinate(Coordinate.newInstance(10, 10)).build()).build();
        VehicleImpl v = VehicleImpl.Builder.newInstance("v").setStartLocation(Location.Builder.newInstance().setCoordinate(Coordinate.newInstance(0, 0)).build()).build();
        VehicleRoutingProblem vrp = VehicleRoutingProblem.Builder.newInstance().addJob(s1).addJob(s2).addJob(s3).addVehicle(v).build();
        RuinWorst worst = new RuinWorst(vrp, 1);
        VehicleRoute route = VehicleRoute.Builder.newInstance(v).addService(s1).addService(s2).addService(s3).setJobActivityFactory(vrp.getJobActivityFactory()).build();
        Collection<Job> unassigned = worst.ruinRoutes(Arrays.asList(route));
        assertEquals(1, unassigned.size());
    }

    @Test
    @DisplayName("It Should Remove Worst")
    void itShouldRemoveWorst() {
        Service s1 = Service.Builder.newInstance("s1").setLocation(Location.Builder.newInstance().setCoordinate(Coordinate.newInstance(1, 1)).build()).build();
        Service s2 = Service.Builder.newInstance("s2").setLocation(Location.Builder.newInstance().setCoordinate(Coordinate.newInstance(3, 1)).build()).build();
        Service s3 = Service.Builder.newInstance("s3").setLocation(Location.Builder.newInstance().setCoordinate(Coordinate.newInstance(10, 10)).build()).build();
        VehicleImpl v = VehicleImpl.Builder.newInstance("v").setStartLocation(Location.Builder.newInstance().setCoordinate(Coordinate.newInstance(0, 0)).build()).build();
        VehicleRoutingProblem vrp = VehicleRoutingProblem.Builder.newInstance().addJob(s1).addJob(s2).addJob(s3).addVehicle(v).build();
        RuinWorst worst = new RuinWorst(vrp, 1);
        VehicleRoute route = VehicleRoute.Builder.newInstance(v).addService(s1).addService(s2).addService(s3).setJobActivityFactory(vrp.getJobActivityFactory()).build();
        Collection<Job> unassigned = worst.ruinRoutes(Arrays.asList(route));
        assertEquals(s3, unassigned.iterator().next());
    }

    @Test
    @DisplayName("It Should Remove Worst Two")
    void itShouldRemoveWorstTwo() {
        Service s1 = Service.Builder.newInstance("s1").setLocation(Location.Builder.newInstance().setCoordinate(Coordinate.newInstance(1, 1)).build()).build();
        Service s2 = Service.Builder.newInstance("s2").setLocation(Location.Builder.newInstance().setCoordinate(Coordinate.newInstance(3, 1)).build()).build();
        Service s3 = Service.Builder.newInstance("s3").setLocation(Location.Builder.newInstance().setCoordinate(Coordinate.newInstance(10, 10)).build()).build();
        VehicleImpl v = VehicleImpl.Builder.newInstance("v").setStartLocation(Location.Builder.newInstance().setCoordinate(Coordinate.newInstance(0, 0)).build()).build();
        VehicleRoutingProblem vrp = VehicleRoutingProblem.Builder.newInstance().addJob(s1).addJob(s2).addJob(s3).addVehicle(v).build();
        RuinWorst worst = new RuinWorst(vrp, 1);
        worst.setRuinShareFactory(new RuinShareFactory() {

            @Override
            public int createNumberToBeRemoved() {
                return 2;
            }
        });
        VehicleRoute route = VehicleRoute.Builder.newInstance(v).addService(s1).addService(s2).addService(s3).setJobActivityFactory(vrp.getJobActivityFactory()).build();
        Collection<Job> unassigned = worst.ruinRoutes(Arrays.asList(route));
        assertTrue(unassigned.size() == 2);
        assertTrue(unassigned.contains(s2));
        assertTrue(unassigned.contains(s3));
    }

    @Test
    @DisplayName("It Should Remove Shipment")
    void itShouldRemoveShipment() {
        Service s1 = Service.Builder.newInstance("s1").setLocation(Location.Builder.newInstance().setCoordinate(Coordinate.newInstance(1, 1)).build()).build();
        Service s2 = Service.Builder.newInstance("s2").setLocation(Location.Builder.newInstance().setCoordinate(Coordinate.newInstance(3, 1)).build()).build();
        Service s3 = Service.Builder.newInstance("s3").setLocation(Location.Builder.newInstance().setCoordinate(Coordinate.newInstance(10, 10)).build()).build();
        Shipment shipment = Shipment.Builder.newInstance("ship1").setPickupLocation(Location.Builder.newInstance().setCoordinate(Coordinate.newInstance(2, 2)).build()).setDeliveryLocation(Location.Builder.newInstance().setCoordinate(Coordinate.newInstance(9, 9)).build()).build();
        VehicleImpl v = VehicleImpl.Builder.newInstance("v").setStartLocation(Location.Builder.newInstance().setCoordinate(Coordinate.newInstance(0, 0)).build()).build();
        VehicleRoutingProblem vrp = VehicleRoutingProblem.Builder.newInstance().addJob(shipment).addJob(s1).addJob(s2).addJob(s3).addVehicle(v).build();
        RuinWorst worst = new RuinWorst(vrp, 1);
        worst.setRuinShareFactory(new RuinShareFactory() {

            @Override
            public int createNumberToBeRemoved() {
                return 1;
            }
        });
        VehicleRoute route = VehicleRoute.Builder.newInstance(v).addPickup(shipment).addService(s1).addService(s2).addService(s3).addDelivery(shipment).setJobActivityFactory(vrp.getJobActivityFactory()).build();
        Collection<Job> unassigned = worst.ruinRoutes(Arrays.asList(route));
        assertTrue(unassigned.size() == 1);
        assertTrue(unassigned.contains(shipment));
    }

    @Test
    @DisplayName("It Should Remove Shipment From Second Route")
    void itShouldRemoveShipmentFromSecondRoute() {
        Service s1 = Service.Builder.newInstance("s1").setLocation(Location.Builder.newInstance().setCoordinate(Coordinate.newInstance(1, 1)).build()).build();
        Service s2 = Service.Builder.newInstance("s2").setLocation(Location.Builder.newInstance().setCoordinate(Coordinate.newInstance(3, 1)).build()).build();
        Service s3 = Service.Builder.newInstance("s3").setLocation(Location.Builder.newInstance().setCoordinate(Coordinate.newInstance(10, 10)).build()).build();
        Shipment shipment = Shipment.Builder.newInstance("ship1").setPickupLocation(Location.Builder.newInstance().setCoordinate(Coordinate.newInstance(3, 1)).build()).setDeliveryLocation(Location.Builder.newInstance().setCoordinate(Coordinate.newInstance(10, 10.1)).build()).build();
        VehicleImpl v = VehicleImpl.Builder.newInstance("v").setStartLocation(Location.Builder.newInstance().setCoordinate(Coordinate.newInstance(0, 0)).build()).build();
        VehicleImpl v2 = VehicleImpl.Builder.newInstance("v2").setStartLocation(Location.Builder.newInstance().setCoordinate(Coordinate.newInstance(0, 0)).build()).build();
        VehicleRoutingProblem vrp = VehicleRoutingProblem.Builder.newInstance().addJob(shipment).addJob(s1).addJob(s2).addJob(s3).addVehicle(v).addVehicle(v2).build();
        RuinWorst worst = new RuinWorst(vrp, 1);
        worst.setRuinShareFactory(new RuinShareFactory() {

            @Override
            public int createNumberToBeRemoved() {
                return 1;
            }
        });
        VehicleRoute route1 = VehicleRoute.Builder.newInstance(v).addService(s1).addService(s2).addService(s3).setJobActivityFactory(vrp.getJobActivityFactory()).build();
        VehicleRoute route2 = VehicleRoute.Builder.newInstance(v2).addPickup(shipment).addDelivery(shipment).build();
        Collection<Job> unassigned = worst.ruinRoutes(Arrays.asList(route1, route2));
        assertTrue(unassigned.size() == 1);
        assertTrue(unassigned.contains(shipment));
    }

    @Test
    @DisplayName("It Should Remove Service And Shipment From Second Route")
    void itShouldRemoveServiceAndShipmentFromSecondRoute() {
        Service s1 = Service.Builder.newInstance("s1").setLocation(Location.Builder.newInstance().setCoordinate(Coordinate.newInstance(1, 1)).build()).build();
        Service s2 = Service.Builder.newInstance("s2").setLocation(Location.Builder.newInstance().setCoordinate(Coordinate.newInstance(3, 1)).build()).build();
        Service s3 = Service.Builder.newInstance("s3").setLocation(Location.Builder.newInstance().setCoordinate(Coordinate.newInstance(10, 10)).build()).build();
        Shipment shipment = Shipment.Builder.newInstance("ship1").setPickupLocation(Location.Builder.newInstance().setCoordinate(Coordinate.newInstance(3, 1)).build()).setDeliveryLocation(Location.Builder.newInstance().setCoordinate(Coordinate.newInstance(10, 10.1)).build()).build();
        VehicleImpl v = VehicleImpl.Builder.newInstance("v").setStartLocation(Location.Builder.newInstance().setCoordinate(Coordinate.newInstance(0, 0)).build()).build();
        VehicleImpl v2 = VehicleImpl.Builder.newInstance("v2").setStartLocation(Location.Builder.newInstance().setCoordinate(Coordinate.newInstance(0, 0)).build()).build();
        VehicleRoutingProblem vrp = VehicleRoutingProblem.Builder.newInstance().addJob(shipment).addJob(s1).addJob(s2).addJob(s3).addVehicle(v).addVehicle(v2).build();
        RuinWorst worst = new RuinWorst(vrp, 1);
        worst.setRuinShareFactory(new RuinShareFactory() {

            @Override
            public int createNumberToBeRemoved() {
                return 2;
            }
        });
        VehicleRoute route1 = VehicleRoute.Builder.newInstance(v).addService(s1).addService(s2).addService(s3).setJobActivityFactory(vrp.getJobActivityFactory()).build();
        VehicleRoute route2 = VehicleRoute.Builder.newInstance(v2).addPickup(shipment).addDelivery(shipment).build();
        Collection<Job> unassigned = worst.ruinRoutes(Arrays.asList(route1, route2));
        assertTrue(unassigned.size() == 2);
        assertTrue(unassigned.contains(shipment));
        assertTrue(unassigned.contains(s3));
    }

    @Test
    @DisplayName("It Should Remove The Same Jobs As A Full Recomputation After Each Removal")
    void itShouldRemoveTheSameJobsAsAFullRecomputation() {
        Random random = new Random(42);
        VehicleRoutingProblem.Builder builder = VehicleRoutingProblem.Builder.newInstance();
        List<VehicleImpl> vehicles = new ArrayList<>();
        for (int v = 0; v < 4; v++) {
            VehicleImpl vehicle = VehicleImpl.Builder.newInstance("v" + v)
                .setStartLocation(Location.Builder.newInstance().setCoordinate(Coordinate.newInstance(50, 50)).build()).build();
            vehicles.add(vehicle);
            builder.addVehicle(vehicle);
        }
        List<Service> services = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            Service service = Service.Builder.newInstance("s" + i).setLocation(Location.Builder.newInstance()
                .setCoordinate(Coordinate.newInstance(random.nextDouble() * 100, random.nextDouble() * 100)).build()).build();
            services.add(service);
            builder.addJob(service);
        }
        VehicleRoutingProblem vrp = builder.build();

        RuinWorst worst = new RuinWorst(vrp, 15);
        Collection<Job> unassigned = worst.ruinRoutes(buildRoutes(vrp, vehicles, services));

        List<Job> expected = fullRecomputation(vrp, buildRoutes(vrp, vehicles, services), 15, () -> 0);
        assertEquals(expected, new ArrayList<>(unassigned));
    }

    @Test
    @DisplayName("It Should Behave Like A Full Recomputation When Noise Dominates")
    void itShouldBehaveLikeAFullRecomputationWhenNoiseDominates() {
        // Noise as configured by Jsprit (20% chance, up to 15% of the max costs), much larger than savings. The
        // noise must be drawn again for all jobs before each removal: drawing it once per route computation makes
        // the ruin keep removing jobs from the route it just changed, and remove jobs with much lower savings.
        Random random = new Random(1);
        VehicleRoutingProblem.Builder builder = VehicleRoutingProblem.Builder.newInstance();
        List<VehicleImpl> vehicles = new ArrayList<>();
        for (int v = 0; v < 16; v++) {
            VehicleImpl vehicle = VehicleImpl.Builder.newInstance("v" + v)
                .setStartLocation(Location.Builder.newInstance().setCoordinate(Coordinate.newInstance(500, 500)).build()).build();
            vehicles.add(vehicle);
            builder.addVehicle(vehicle);
        }
        List<Service> services = new ArrayList<>();
        for (int i = 0; i < 400; i++) {
            Service service = Service.Builder.newInstance("s" + i).setLocation(Location.Builder.newInstance()
                .setCoordinate(Coordinate.newInstance(random.nextDouble() * 1000, random.nextDouble() * 1000)).build()).build();
            services.add(service);
            builder.addJob(service);
        }
        VehicleRoutingProblem vrp = builder.build();

        int runs = 200;
        int toRemove = 30;
        double[] removedSavings = new double[2];
        Random noiseRandom = new Random(99);
        NoiseMaker noise = () -> noiseRandom.nextDouble() < 0.2 ? 0.15 * 100_000 * noiseRandom.nextDouble() : 0.;
        for (int version = 0; version < 2; version++) {
            for (int run = 0; run < runs; run++) {
                List<VehicleRoute> routes = buildRoutes(vrp, vehicles, services);
                Map<Job, Double> savingsBefore = new HashMap<>();
                for (VehicleRoute route : routes) {
                    List<TourActivity> acts = new ArrayList<>(route.getActivities());
                    for (int i = 0; i < acts.size(); i++) {
                        savingsBefore.put(((TourActivity.JobActivity) acts.get(i)).getJob(), savings(vrp, route, acts, i));
                    }
                }
                Collection<Job> removed;
                if (version == 0) {
                    removed = fullRecomputation(vrp, routes, toRemove, noise);
                } else {
                    RuinWorst worst = new RuinWorst(vrp, toRemove);
                    worst.setNoiseMaker(noise);
                    removed = worst.ruinRoutes(routes);
                }
                for (Job job : removed) {
                    removedSavings[version] += savingsBefore.get(job) / runs / toRemove;
                }
            }
        }
        assertEquals(removedSavings[0], removedSavings[1], removedSavings[0] * 0.05,
            "mean savings of removed jobs: full recomputation " + removedSavings[0] + ", RuinWorst " + removedSavings[1]);
    }

    /**
     * Reference: the previous algorithm, recomputing the savings (with noise) of all routes before each removal.
     */
    private static List<Job> fullRecomputation(VehicleRoutingProblem vrp, List<VehicleRoute> routes, int toRemove, NoiseMaker noise) {
        List<Job> removed = new ArrayList<>();
        for (int removal = 0; removal < toRemove; removal++) {
            Job worstJob = null;
            VehicleRoute worstRoute = null;
            double bestSavings = Double.MIN_VALUE;
            for (VehicleRoute route : routes) {
                List<TourActivity> acts = new ArrayList<>(route.getActivities());
                for (int i = 0; i < acts.size(); i++) {
                    double savings = Math.max(0, savings(vrp, route, acts, i) + noise.makeNoise());
                    if (savings > bestSavings) {
                        bestSavings = savings;
                        worstJob = ((TourActivity.JobActivity) acts.get(i)).getJob();
                        worstRoute = route;
                    }
                }
            }
            worstRoute.getTourActivities().removeJob(worstJob);
            removed.add(worstJob);
        }
        return removed;
    }

    private static double savings(VehicleRoutingProblem vrp, VehicleRoute route, List<TourActivity> acts, int i) {
        TourActivity before = i == 0 ? route.getStart() : acts.get(i - 1);
        TourActivity after = i == acts.size() - 1 ? route.getEnd() : acts.get(i + 1);
        return c(vrp, route, before, acts.get(i)) + c(vrp, route, acts.get(i), after) - c(vrp, route, before, after);
    }

    private static List<VehicleRoute> buildRoutes(VehicleRoutingProblem vrp, List<VehicleImpl> vehicles, List<Service> services) {
        List<VehicleRoute> routes = new ArrayList<>();
        for (int v = 0; v < vehicles.size(); v++) {
            VehicleRoute.Builder route = VehicleRoute.Builder.newInstance(vehicles.get(v)).setJobActivityFactory(vrp.getJobActivityFactory());
            for (int i = v; i < services.size(); i += vehicles.size()) {
                route.addService(services.get(i));
            }
            routes.add(route.build());
        }
        return routes;
    }

    private static double c(VehicleRoutingProblem vrp, VehicleRoute route, TourActivity from, TourActivity to) {
        return vrp.getTransportCosts().getTransportCost(from.getLocation(), to.getLocation(), from.getEndTime(),
            DriverImpl.noDriver(), route.getVehicle());
    }
}
