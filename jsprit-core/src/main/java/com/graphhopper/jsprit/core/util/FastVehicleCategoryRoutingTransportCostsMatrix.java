package com.graphhopper.jsprit.core.util;

import com.graphhopper.jsprit.core.problem.Location;
import com.graphhopper.jsprit.core.problem.cost.AbstractForwardVehicleRoutingTransportCosts;
import com.graphhopper.jsprit.core.problem.driver.Driver;
import com.graphhopper.jsprit.core.problem.vehicle.Vehicle;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleTypeImpl;


public class FastVehicleCategoryRoutingTransportCostsMatrix extends AbstractForwardVehicleRoutingTransportCosts {

    /**
     * Builder that builds the matrix.
     *
     * @author schroeder
     */
    public static class Builder {

        private boolean isSymmetric;

        private int vehicleCategoryCount;

        private double[][][] matrix;

        private final int noLocations;

        /**
         * Creates a new builder returning the matrix-builder.
         * <p>If you want to consider symmetric matrices, set isSymmetric to true.
         *
         * @param isSymmetric true if matrix is symmetric, false otherwise
         * @param vehicleCategoryCount the count of type of vehicle in the fleet
         * @return builder
         */
        public static Builder newInstance(int noLocations, boolean isSymmetric, int vehicleCategoryCount) {
            return new Builder(noLocations, isSymmetric, vehicleCategoryCount);
        }

        private Builder(int noLocations, boolean isSymmetric, int vehicleCategoryCount) {
            this.isSymmetric = isSymmetric;
            this.vehicleCategoryCount = vehicleCategoryCount;
            matrix = new double[noLocations][noLocations][2 * vehicleCategoryCount];
            this.noLocations = noLocations;
        }

        /**
         * Adds a transport-distance for a particular relation.
         *
         * @param fromIndex from location index
         * @param toIndex   to location index
         * @param distance  the distance to be added
         * @param vehicleCategory the category of the vehicle for this distance
         * @return builder
         */
        public Builder addTransportDistance(int fromIndex, int toIndex, double distance, int vehicleCategory) {
            add(fromIndex, toIndex, vehicleCategory * 2, distance);
            return this;
        }

        private void add(int fromIndex, int toIndex, int indicatorIndex, double value) {
            if (isSymmetric) {
                if (fromIndex < toIndex) matrix[fromIndex][toIndex][indicatorIndex] = value;
                else matrix[toIndex][fromIndex][indicatorIndex] = value;
            } else matrix[fromIndex][toIndex][indicatorIndex] = value;
        }

        /**
         * Adds transport-time for a particular relation.
         *
         * @param fromIndex from location index
         * @param toIndex   to location index
         * @param time      the time to be added
         * @param vehicleCategory the category of the vehicle for this time
         * @return builder
         */
        public Builder addTransportTime(int fromIndex, int toIndex, double time, int vehicleCategory) {
            add(fromIndex, toIndex, vehicleCategory * 2 + 1, time);
            return this;
        }

        public Builder addTransportTimeAndDistance(int fromIndex, int toIndex, double time, double distance, int vehicleCategory) {
            addTransportTime(fromIndex, toIndex, time, vehicleCategory);
            addTransportDistance(fromIndex, toIndex, distance, vehicleCategory);
            return this;
        }
        /**
         * Builds the matrix.
         *
         * @return matrix
         */
        public FastVehicleCategoryRoutingTransportCostsMatrix build() {
            return new FastVehicleCategoryRoutingTransportCostsMatrix(this);
        }


    }

    private final boolean isSymmetric;
    private final int vehicleCategoryCount;

    private final double[][][] matrix;

    private int noLocations;

    private FastVehicleCategoryRoutingTransportCostsMatrix(Builder builder) {
        this.isSymmetric = builder.isSymmetric;
        this.vehicleCategoryCount = builder.vehicleCategoryCount;
        matrix = builder.matrix;
        noLocations = builder.noLocations;
    }

    /**
     * First dim is from, second to and third indicates whether it is a distance value (index=0) or time value (index=1).
     *
     * @return
     */
    public double[][][] getMatrix() {
        return matrix;
    }

    @Override
    public double getTransportTime(Location from, Location to, double departureTime, Driver driver, Vehicle vehicle) {
        if (from.getIndex() < 0 || to.getIndex() < 0)
            throw new IllegalArgumentException("index of from " + from + " to " + to + " < 0 ");
        int timeIndex = 1;
        if (vehicle != null && vehicle.getType() != null && vehicle.getType().getCategoryId() < vehicleCategoryCount) {
            timeIndex = vehicle.getType().getCategoryId() * 2 + 1;
        }
        return get(from.getIndex(), to.getIndex(), timeIndex);
    }

    private double get(int from, int to, int indicatorIndex) {
        double value;
        if (isSymmetric) {
            if (from < to) value = matrix[from][to][indicatorIndex];
            else value = matrix[to][from][indicatorIndex];
        } else {
            value = matrix[from][to][indicatorIndex];
        }
        return value;
    }

    /**
     * Returns the distance from to to.
     *
     * @param fromIndex from location index
     * @param toIndex   to location index
     * @param vehicle   the vehicle
     * @return the distance
     */
    private double getDistance(int fromIndex, int toIndex, Vehicle vehicle) {
        int distanceIndex = 0;
        if (vehicle != null && vehicle.getType().getCategoryId() < vehicleCategoryCount) {
            distanceIndex = vehicle.getType().getCategoryId() * 2;
        }
        return get(fromIndex, toIndex, distanceIndex);
    }

    @Override
    public double getDistance(Location from, Location to, double departureTime, Vehicle vehicle) {
        return getDistance(from.getIndex(), to.getIndex(), vehicle);
    }

    @Override
    public double getTransportCost(Location from, Location to, double departureTime, Driver driver, Vehicle vehicle) {
        if (from.getIndex() < 0 || to.getIndex() < 0)
            throw new IllegalArgumentException("index of from " + from + " to " + to + " < 0 ");
        if (vehicle == null)
            return getDistance(from.getIndex(), to.getIndex(), null);
        VehicleTypeImpl.VehicleCostParams costParams = vehicle.getType().getVehicleCostParams();
        return costParams.perDistanceUnit * getDistance(from.getIndex(), to.getIndex(), vehicle) + costParams.perTransportTimeUnit * getTransportTime(from, to, departureTime, driver, vehicle);
    }

    public int getNoLocations() {
        return noLocations;
    }


}
