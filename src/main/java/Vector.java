import java.util.ArrayList;

public class Vector {
    ArrayList<Double> vector = new ArrayList<Double>();

    public Vector(){ }
    public Vector(ArrayList<Double> vector){
        this.vector = vector;
    }

    public ArrayList<Double> getCoordinates() {
        return vector;
    }

    public int Size(){
        return vector.size();
    }

    public void set(int index, double value) {
        vector.set(index, value);
    }

    public double get(int index) {
        return vector.get(index);
    }

    public Vector Sum(Vector other){
        if(other.Size() != this.Size()){
            throw new IllegalArgumentException("Vector sizes do not match");
        }

        ArrayList<Double> sumVector = new ArrayList<Double>();

        for (int i = 0; i < other.Size(); i++) {
            sumVector.add(this.vector.get(i) + other.vector.get(i));
        }

        return new Vector(sumVector);
    }

    public Vector Scale(double factor){
        ArrayList<Double> sumVector = new ArrayList<Double>();

        for (int i = 0; i < this.Size(); i++) {
            sumVector.add(this.vector.get(i)*factor);
        }
        return new Vector(sumVector);
    }

    public double Dot(Vector other) {
        if (this.Size() != other.Size()) {
            throw new IllegalArgumentException("Sizes don't match");
        }

        double sum = 0;
        for (int i = 0; i < this.Size(); i++) {
            sum += this.vector.get(i) * other.vector.get(i);
        }
        return sum;
    }

    public double Norm(){
        double total = 0;

        for (int i = 0; i < this.Size(); i++) {
            total += this.vector.get(i) * this.vector.get(i);
        }

        return Math.sqrt(total);
    }

    public double Cosine(Vector other){

        return (Dot(other)/(this.Norm() * other.Norm()));
    }

    public static Vector zero(int dimension) {
        ArrayList<Double> coords = new ArrayList<>();
        for (int i = 0; i < dimension; i++) {
            coords.add(0.0);
        }
        return new Vector(coords);
    }

}


