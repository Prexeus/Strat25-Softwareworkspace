package com.example.model;

public class BackboneCalculator implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    private double breadFactor = 1.5;
    private double housingFactor = 1;
    private double healthFactor = 0.75;

    private double breadWorth = 0.780796;
    private double housing1Worth = 2.119305;
    private double housing2Worth = 4.796323;
    private double health1Worth = 2.119305;
    private double health2Worth = 4.796323;

    public double calculateBackboneInfluence(int bread, int housing1, int housing2, int health1, int health2) {
        return (bread * breadFactor * breadWorth) 
                + (housing1 * housingFactor * housing1Worth)
                + (housing2 * housingFactor * housing2Worth) 
                + (health1 * healthFactor * health1Worth)
                + (health2 * healthFactor * health2Worth);
    }

    public double getBreadFactor() {
        return breadFactor;
    }

    public double getHousingFactor() {
        return housingFactor;
    }

    public double getHealthFactor() {
        return healthFactor;
    }

    public void setBreadFactor(double breadFactor) {
        this.breadFactor = breadFactor;
    }

    public void setHousingFactor(double housingFactor) {
        this.housingFactor = housingFactor;
    }

    public void setHealthFactor(double healthFactor) {
        this.healthFactor = healthFactor;
    }

}
