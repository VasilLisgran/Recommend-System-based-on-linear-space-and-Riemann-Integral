package recommender.Model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MyVector {
    private final List<Double> coordinates;

    public MyVector(){
        this.coordinates = new ArrayList<>();
    }

    public MyVector(List<Double> coordinates){
        this.coordinates = new ArrayList<>(coordinates);
    }

    // Sum
    public MyVector add(MyVector other){
        if(other.size() != this.size()){
            throw new IllegalArgumentException("MyVector sizes do not match");
        }

        List<Double> result = new ArrayList<>();
        for (int i = 0; i < other.size(); i++) {
            result.add(this.coordinates.get(i) + other.coordinates.get(i));
        }
        return new MyVector(result);
    }

    public MyVector scale(double factor){
        List<Double> result = new ArrayList<>();
        for (int i = 0; i < this.size(); i++) {
            result.add(this.coordinates.get(i)*factor);
        }
        return new MyVector(result);
    }

    public double dot(MyVector other) {
        if (this.size() != other.size()) {
            throw new IllegalArgumentException("Sizes don't match");
        }

        double sum = 0;
        for (int i = 0; i < this.size(); i++) {
            sum += this.coordinates.get(i) * other.coordinates.get(i);
        }
        return sum;
    }

    public double norm(){
        double total = 0;
        for (double value : coordinates) {
            total += value * value;
        }
        return Math.sqrt(total);
    }

    public double cosine(MyVector other) {
        double dotProduct = this.dot(other);
        double normProduct = this.norm() * other.norm();

        if (normProduct == 0) {
            return 0;
        }
        return dotProduct / normProduct;
    }

    public static MyVector zero(int dimension) {
        List<Double> coords = new ArrayList<>(Collections.nCopies(dimension, 0.0));
        return new MyVector(coords);
    }

    public List<Double> getCoordinates() {
        return new ArrayList<>(coordinates);    // It's better to return the copy because of risks of destroying main version of this vector
    }

    public int size(){
        return coordinates.size();
    }

    public void set(int index, double value) {
        if(index < 0 || index >= coordinates.size()){
            throw new IndexOutOfBoundsException("Index: " + index);
        }
        coordinates.set(index, value);
    }

    public double get(int index) {
        if(index < 0 || index >= coordinates.size()){
            throw new IndexOutOfBoundsException("Index: " + index);
        }
        return coordinates.get(index);
    }

    @Override
    public String toString() {
        return coordinates.toString();
    }
}