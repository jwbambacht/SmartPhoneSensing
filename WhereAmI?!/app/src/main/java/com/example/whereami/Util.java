package com.example.whereami;

import android.content.SharedPreferences;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Util  extends AppCompatActivity {

    // Method that retrieves all trained samples from the sharedPreferences storage
    // Samples are first converted from json to objects
    static List<Sample> loadSamples(SharedPreferences sharedPreferences) {

        Gson gson = new Gson();
        String json = sharedPreferences.getString("samples",null);
        Type type = new TypeToken<ArrayList<Sample>>() {}.getType();
        List<Sample> samples = gson.fromJson(json,type);

        if(samples == null) {
            samples = new ArrayList<>();
        }

        Log.i("Total saved samples",""+samples.size());

//        for(Sample s : samples) {
//            HashMap<String,Integer> networks = s.getNetworks();
//            Log.i("BSSID/RSSI for ","network with cellID " + s.getCellID());
//
//            for (Map.Entry<String,Integer> entry : networks.entrySet()) {
//                Log.i("", entry.getKey() + " / " + entry.getValue());
//            }
//        }

        return samples;
    }

    // Method that saves all trained samples to the sharedPreferences
    // Before saving the objects are converted into json
    static void saveSamples(SharedPreferences sharedPreferences, List<Sample> samples) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        Gson gson = new Gson();
        String json = gson.toJson(samples);
        editor.putString("samples",json);
        editor.apply();

        Log.i("Samples saved!","");
    }

    // Method that removes all saved samples
    static void resetSamples(SharedPreferences sharedPreferences) {
        try {
            List<Sample> samples = new ArrayList<>();
            saveSamples(sharedPreferences,samples);
        }catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // Method that retrieves the cell precision the user has selected in settings activity
    static int getPrecision(SharedPreferences sharedPreferences) {
        try {
            return sharedPreferences.getInt("precision",4);
        }catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // Method that saves the cell precision the user has selected in the settings activity
    static void setPrecision(SharedPreferences sharedPreferences, int precision) {
        try {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putInt("precision",precision);
            editor.commit();
        }catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // Method that retrieves the method of calculation for localization the user has selected in settings activity
    static String getMethod(SharedPreferences sharedPreferences) {
        try {
            return sharedPreferences.getString("method", "KNN");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // Method that saves the methof of calculation for localization the user has selected in the settings activity
    static void setMethod(SharedPreferences sharedPreferences, String method) {
        try {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString("method",method);
            editor.commit();
        }catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // Method that performs a network scan, repeating in rounds and take average RSSI for each network
    static HashMap<String,Integer> findNetworks(WifiManager wifiManager, int rounds) {
        HashMap<String, Integer> networks = new HashMap<String, Integer>();
        HashMap<String, int[]> tempNetworks = new HashMap<String, int[]>();

        for (int i = 0; i < rounds; i++) {
            wifiManager.startScan();
            List<ScanResult> scanResults = wifiManager.getScanResults();

            // Sense networks, apply rounds and take the average value for RSSI to correct for outliers
            for (ScanResult scan : scanResults) {
                String BSSID = scan.BSSID;

                int[] rssi;

                if (tempNetworks.containsKey(BSSID)) {
                    rssi = tempNetworks.get(BSSID);
                    tempNetworks.remove(BSSID);
                } else {
                    rssi = new int[rounds];
                }

                rssi[i] = scan.level;

                tempNetworks.put(BSSID, rssi);
            }

            try {
                Thread.sleep(200);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }

        for (Map.Entry<String, int[]> entry : tempNetworks.entrySet()) {
            String BSSID = entry.getKey();
            int RSSI = Util.calculateAverageInArray(entry.getValue(), rounds);

            networks.put(BSSID, RSSI);
        }
        return networks;
    }

    // Method that determines the KNN of the sensed networks and returns the predicted cell back
    public static String KNN(HashMap<String, Integer> sensedNetworks, List<Sample> allSamples, SharedPreferences settingsSharedPreferences, String[] cells) {

        List<Result> results = new ArrayList<>();

        int cellPrecision = Util.getPrecision(settingsSharedPreferences);
        double[] cellCounts = new double[cellPrecision];
        int k = Util.calculateK(allSamples.size());

        // For each trained sample we want to compute the distance to the sensed sample
        for(Sample sample : allSamples) {
            double distance = 0;

            HashMap<String,Integer> networks = sample.getNetworks();

            // Determine the networks in a trained sample against the sensed sample
            for(Map.Entry<String,Integer> entry : networks.entrySet()) {
                String BSSID = entry.getKey();
                Integer RSSI = entry.getValue();

                // If the network is in both samples, the distance can easily be computed as the euclidean distance
                // If the network is not scanned in the sensed sample, we use a RSSI of -100 (meaning it is as weak as possible)
                if(sensedNetworks.containsKey(BSSID)) {
                    distance += Math.pow(Math.abs(RSSI-sensedNetworks.get(BSSID)),2);
                }else{
                    distance += Math.pow(Math.abs(RSSI+100),2);
                }
            }

            // Determine the networks that are scanned (only) in the sensed sample but not in the trained sample
            // We also use a RSSI value of -100 for the not scanned network
            for(Map.Entry<String,Integer> entry : sensedNetworks.entrySet()) {
                String BSSID = entry.getKey();
                Integer RSSI = entry.getValue();

                if(!networks.containsKey(BSSID)) {
                    distance += Math.pow(Math.abs(RSSI+100),2);
                }
            }

            results.add(new Result(sample, Math.sqrt(distance)));
        }

        // Sort the distances in increasing order
        Collections.sort(results, (a, b) -> a.getDistance() < b.getDistance() ? -1 : a.getDistance() == b.getDistance() ? 0 : 1);

        // Take the k nearest networks (with lowest distance)
        List<Result> kresults = results.subList(0,k);

        // Count the number of neighbors in each belonging in each cell
        for(Result res : kresults) {
            Log.i(" ",res.toString()+"");
            cellCounts[res.getCellID()]++;
        }

        // Method to give more weight to the nearest neighbor and less weight to the more distant nearest neighbors (for comparison)
        double totalWeight = 0;

        // Compute individual weight for each result
        for(Result res : kresults) {
            double weight = 1/res.getDistance();
            res.setWeight(weight);
            totalWeight += weight;
        }

        // Normalize the weight for each result
        for(Result res : kresults) {
            res.normalizeWeight(totalWeight);
        }

        // Compute how much weight contributes to each cell prediction
        double[] weightPerCell = new double[cellPrecision];

        for(Result res : kresults) {
            weightPerCell[res.getCellID()] += res.getWeight();
        }

        // Display results of weight per cell and based on majority k-nearest-neighbors only
        Log.i("k=",k+"");
        for(int i = 0; i < cellPrecision; i++) {
            Log.i("Total weight for "+cells[i]," is "+weightPerCell[i]);
        }
        Log.i("Take only cell count:", Arrays.toString(cellCounts));

        // Find the index of the cell with the maximum value
        int largestIndexCount = Util.findMaxInArray(cellCounts);
        int largestIndexWeights = Util.findMaxInArray(weightPerCell);

        return "Cell Count: " + cells[largestIndexCount]+" \nCell Weight: "+cells[largestIndexWeights];
    }

    // Method that calculates the k-value based on the size of the trained samples
    // It is researched and proven that the optimal value of 7 delivers best results.
    // If there aren't enough samples the value of k will be equal to the square root of the sample size.
    static int calculateK(int size) {
        int k = (int) Math.floor(Math.sqrt(size));

        if(k > 7) {
            return 7;
        }

        return k;
    }

    // Method that finds the index of the maximum value in array
    static int findMaxInArray(double[] arr) {
        double largestValue = arr[0];
        int largestIndex = 0;
        for (int i = 1; i < arr.length; i++) {
            if ( arr[i] >= largestValue ) {
                largestValue = arr[i];
                largestIndex = i;
            }
        }
        return largestIndex;
    }

    // Method that calculates the average of elements in array, excluding zero values
    static int calculateAverageInArray(int[] array, int rounds) {
        int nonZeroElements = 0;
        int sum = 0;
        for(int i = 0; i < rounds; i++) {
            if(array[i] < 0) {
                nonZeroElements++;
                sum += array[i];
            }
        }

        return (int) Math.floor(sum/nonZeroElements);
    }
}
