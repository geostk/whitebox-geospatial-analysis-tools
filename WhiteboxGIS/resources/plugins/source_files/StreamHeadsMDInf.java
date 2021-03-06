/*
 * Copyright (C) 2014 Jan Seibert (jan.seibert@geo.uzh.ch) and 
 * Marc Vis (marc.vis@geo.uzh.ch)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package plugins;

import java.util.Date;
import whitebox.geospatialfiles.WhiteboxRaster;
import whitebox.geospatialfiles.WhiteboxRasterBase;
import whitebox.interfaces.WhiteboxPlugin;
import whitebox.interfaces.WhiteboxPluginHost;

/**
 * WhiteboxPlugin is used to define a plugin tool for Whitebox GIS.
 * @author Dr. John Lindsay <jlindsay@uoguelph.ca>
 */
public class StreamHeadsMDInf implements WhiteboxPlugin {

    private WhiteboxPluginHost myHost = null;
    private String[] args;
    
    double pi = Math.PI;
    
    WhiteboxRaster dem;
    WhiteboxRaster output;
    WhiteboxRaster tmpArea;
    WhiteboxRaster tmpCount;

    int depth = 0;
    
    double mdInfPower = 1;
    double caThreshold;
    String caThresholdType;
    
    int[] xd = new int[]{0, -1, -1, -1, 0, 1, 1, 1};
    int[] yd = new int[]{-1, -1, 0, 1, 1, 1, 0, -1};
    double[] dd = new double[]{1, Math.sqrt(2), 1, Math.sqrt(2), 1, Math.sqrt(2), 1, Math.sqrt(2)};
    
    double gridRes = 1;

    /**
     * Used to retrieve the plugin tool's name. This is a short, unique name containing no spaces.
     * @return String containing plugin name.
     */
    @Override
    public String getName() {
        return "StreamHeadsMDInf";
    }
    /**
     * Used to retrieve the plugin tool's descriptive name. This can be a longer name (containing spaces) and is used in the interface to list the tool.
     * @return String containing the plugin descriptive name.
     */
    @Override
    public String getDescriptiveName() {
    	return "Find Streamheads (MDInf)";
    }
    /**
     * Used to retrieve a short description of what the plugin tool does.
     * @return String containing the plugin's description.
     */
    @Override
    public String getToolDescription() {
    	return "Finds streamheads for the specified digital elevation model (DEM) using the MDInf algorithm.";
    }
    /**
     * Used to identify which toolboxes this plugin tool should be listed in.
     * @return Array of Strings.
     */
    @Override
    public String[] getToolbox() {
    	String[] ret = { "Streamheads" };
    	return ret;
    }
    /**
     * Sets the WhiteboxPluginHost to which the plugin tool is tied. This is the class
     * that the plugin will send all feedback messages, progress updates, and return objects.
     * @param host The WhiteboxPluginHost that called the plugin tool.
     */
    @Override
    public void setPluginHost(WhiteboxPluginHost host) {
        myHost = host;
    }
    /**
     * Used to communicate feedback pop-up messages between a plugin tool and the main Whitebox user-interface.
     * @param feedback String containing the text to display.
     */
    private void showFeedback(String message) {
        if (myHost != null) {
            myHost.showFeedback(message);
        } else {
            System.out.println(message);
        }
    }
    /**
     * Used to communicate a return object from a plugin tool to the main Whitebox user-interface.
     * @return Object, such as an output WhiteboxRaster.
     */
    private void returnData(Object ret) {
        if (myHost != null) {
            myHost.returnData(ret);
        }
    }

    private int previousProgress = 0;
    private String previousProgressLabel = "";
    /**
     * Used to communicate a progress update between a plugin tool and the main Whitebox user interface.
     * @param progressLabel A String to use for the progress label.
     * @param progress Float containing the progress value (between 0 and 100).
     */
    private void updateProgress(String progressLabel, int progress) {
        if (myHost != null && ((progress != previousProgress) || 
                (!progressLabel.equals(previousProgressLabel)))) {
            myHost.updateProgress(progressLabel, progress);
        }
        previousProgress = progress;
        previousProgressLabel = progressLabel;
    }
    /**
     * Used to communicate a progress update between a plugin tool and the main Whitebox user interface.
     * @param progress Float containing the progress value (between 0 and 100).
     */
    private void updateProgress(int progress) {
        if (myHost != null && progress != previousProgress) {
            myHost.updateProgress(progress);
        }
        previousProgress = progress;
    }
    /**
     * Sets the arguments (parameters) used by the plugin.
     * @param args 
     */ 
    @Override
    public void setArgs(String[] args) {
        this.args = args.clone();
    }
    
    private boolean cancelOp = false;
    /**
     * Used to communicate a cancel operation from the Whitebox GUI.
     * @param cancel Set to true if the plugin should be canceled.
     */
    @Override
    public void setCancelOp(boolean cancel) {
        cancelOp = cancel;
    }
    
    private void cancelOperation() {
        showFeedback("Operation cancelled.");
        updateProgress("Progress: ", 0);
    }
    
    private boolean amIActive = false;
    /**
     * Used by the Whitebox GUI to tell if this plugin is still running.
     * @return a boolean describing whether or not the plugin is actively being used.
     */
    @Override
    public boolean isActive() {
        return amIActive;
    }

    @Override
    public void run() {
        amIActive = true;

        String demHeader = null;
        String outputHeader = null;
        
        int numRows;
        int numCols;
        int row;
        int col;
        double z;
        int i;
        int c;
        int x, y;

        double noData;
        
        float progress = 0;
        
        if (args.length <= 0) {
            showFeedback("Plugin parameters have not been set.");
            return;
        }
        
        for (i = 0; i < args.length; i++) {
            if (i == 0) {
                demHeader = args[i];
            } else if (i == 1) {
                outputHeader = args[i];
            } else if (i == 2) {
                mdInfPower = Double.parseDouble(args[i]);
            } else if (i == 3) {
                caThreshold = Double.parseDouble(args[i]);
            } else if (i == 4) {
                caThresholdType = args[i].toLowerCase();
            }
        }

        // check to see that the inputHeader and outputHeader are not null.
        if ((demHeader == null) || (outputHeader == null)) {
            showFeedback("One or more of the input parameters have not been set properly.");
            return;
        }

        try {
            dem = new WhiteboxRaster(demHeader, "r");
            
            numRows = dem.getNumberRows();
            numCols = dem.getNumberColumns();
            noData = dem.getNoDataValue();
            gridRes = dem.getCellSizeX();
            
            output = new WhiteboxRaster(outputHeader, "rw", demHeader, WhiteboxRaster.DataType.FLOAT, 0);
            output.setPreferredPalette("blueyellow.pal");
            output.setDataScale(WhiteboxRasterBase.DataScale.CONTINUOUS);
            output.setZUnits("dimensionless");
            
            tmpArea = new WhiteboxRaster(outputHeader.replace(".dep", "_tmp1.dep"), "rw", demHeader, WhiteboxRaster.DataType.FLOAT, noData);
            tmpArea.isTemporaryFile = true;
            
            tmpCount = new WhiteboxRaster(outputHeader.replace(".dep", "_tmp1.dep"), "rw", demHeader, WhiteboxRaster.DataType.FLOAT, noData);
            tmpCount.isTemporaryFile = true;

            switch (caThresholdType) {
                case "specific catchment area (sca)":
                    caThreshold = caThreshold / gridRes;
                    break;
                case "total catchment area":
                    caThreshold = caThreshold / (gridRes * gridRes);
                    break;
                default: // case "Number of upslope grid cells"
                    break;
            }
            
            // Calculate the number of inflowing neighbours to each grid cell and initialize the output grid
            updateProgress("Loop 1 of 2:", 0);
            for (row = 0; row < numRows; row++) {
                for (col = 0; col < numCols; col++) {
                    z = dem.getValue(row, col);
                    if (z != noData) {
                        i = 0;
                        for (c = 0; c < 8; c++) {
                            x = col + xd[c];
                            y = row + yd[c];
                            if (z < dem.getValue(y, x)) {
                                i++;
                            }
                        }
                        tmpArea.setValue(row, col, 1);
                        tmpCount.setValue(row, col, i);
                        output.setValue(row, col, -1);
                   } else {
                        tmpArea.setValue(row, col, noData);
                        output.setValue(row, col, noData);
                    }
                }
                if (cancelOp) {
                    cancelOperation();
                    return;
                }
                progress = (float) (100f * row / (numRows - 1));
                updateProgress("Loop 1 of 2:", (int) progress);
            }

            // Perform the flow accumulation
            updateProgress("Loop 2 of 2:", 0);
            for (row = 0; row < numRows; row++) {
                for (col = 0; col < numCols; col++) {
                    if (dem.getValue(row, col) != noData){
                        if (tmpCount.getValue(row, col) == 0) { //there are no 
                            //remaining inflowing neighbours, send it's current 
                            //flow accum val downslope
                            MDInfAccum(row, col, mdInfPower, noData);
                        }
                    }
                }
                if (cancelOp) {
                    cancelOperation();
                    return;
                }
                progress = (float) (100f * row / (numRows - 1));
                updateProgress("Loop 2 of 2:", (int) progress);
            }
            
            output.addMetadataEntry("Created by the " + getDescriptiveName() + " tool.");
            output.addMetadataEntry("Created on " + new Date());
            
            dem.close();
            output.close();
            tmpArea.close();
            tmpCount.close();

            // returning a header file string displays the image.
            returnData(outputHeader);

        } catch (Exception e) {
            showFeedback(e.getMessage());
        } finally {
            updateProgress("Progress: ", 0);
            // tells the main application that this process is completed.
            amIActive = false;
            myHost.pluginComplete();
        }
    }

    private void MDInfAccum(int row, int col, double hExp, double noData) {

        double z = dem.getValue(row, col);
        double flowAccumVal = tmpArea.getValue(row, col);
        
        int i, ii;
        double p1, p2;
        double z1, z2;
        double nx, ny, nz;
        double hr, hs;
        double[] rFacet = new double[8];
        double[] sFacet = new double[]{noData, noData, noData, noData, noData, noData, noData, noData};

        double[] valley = new double[8];
        double[] portion = new double[8];
        double valleySum = 0;
        double valleyMax = 0;
        int iMax = 0;
        int a, b;
        int c;

        tmpCount.setValue(row, col, -1);

        if (caThreshold >= flowAccumVal || caThreshold == noData) {

            if (flowAccumVal != noData) {
                output.setValue(row, col, 0);
            }

            // Compute slope and direction for each of the triangular facets
            for (c = 0; c < 8; c++){
                i = c;
                ii = (i + 1) % 8;

                p1 = dem.getValue(row + yd[i], col + xd[i]);
                p2 = dem.getValue(row + yd[ii], col + xd[ii]);
                if ((p1 != noData) && (p2 != noData)) {

                    // Calculate the elevation difference between the centerpoint and the points p1 and p2
                    z1 = p1 - z;
                    z2 = p2 - z;

                    // Calculate the coordinates of the normal to the triangular facet
                    nx = (yd[i] * z2 - yd[ii] * z1) * gridRes;
                    ny = (xd[ii] * z1 - xd[i] * z2) * gridRes;
                    nz = (xd[i] * yd[ii] - xd[ii] * yd[i]) * Math.pow(gridRes, 2);

                    // Calculate the downslope direction of the triangular facet
                    if (nx == 0) {
                        if (ny >= 0) {
                            hr = 0;
                        } else {
                            hr = pi;
                        }
                    } else {
                        if (nx >= 0) {
                            hr = pi / 2 - Math.atan(ny / nx);
                        } else {
                            hr = 3 * pi / 2 - Math.atan(ny / nx);
                        }
                    }

                    // Calculate the slope of the triangular facet
                    hs = -Math.tan(Math.acos(nz / (Math.sqrt(Math.pow(nx, 2) + Math.pow(ny, 2) + Math.pow(nz, 2)))));

                    // If the downslope direction is outside the triangular facet, then use the direction of p1 or p2
                    if ((hr < (i) * pi / 4) || (hr > (i + 1) * pi / 4)) {
                        if (p1 < p2) {
                            hr = i * pi / 4;
                            hs = (z - p1) / (dd[i] * gridRes);
                        } else {
                            hr = ii * pi / 4;
                            hs = (z - p2) / (dd[ii] * gridRes);
                        }
                    }

                    rFacet[c] = hr;
                    sFacet[c] = hs;

                } else {
                    if ((p1 != noData) && (p1 < z)) {
                        hr = ((float) i) / 4 * pi;
                        hs = (z - p1) / (dd[ii] * gridRes);
                        
                        rFacet[c] = hr;
                        sFacet[c] = hs;
                    }
                }
            }

            // Compute the total area of the triangular facets where water is flowing to
            for (c = 0; c < 8; c++){
                i = c;
                ii = (i + 1) % 8;

                if (sFacet[i] > 0) {                                                      // If the slope is downhill
                    if ((rFacet[i] > (i * pi / 4)) && (rFacet[i] < ((i + 1) * pi / 4))) {     // If the downslope direction is inside the 45 degrees of the triangular facet
                        valley[i] = sFacet[i];
                    } else if (rFacet[i] == rFacet[ii]) {                                    // If two adjacent triangular facets have the same downslope direction
                        valley[i] = sFacet[i];
                    } else if ((sFacet[ii] == noData) && (rFacet[i] == ((i + 1) * pi / 4))) {      // If the downslope direction is on the border of the current triangular facet, and the corresponding neigbour// s downslope is NoData
                        valley[i] = sFacet[i];
                    } else {
                        ii = (i + 7) % 8;
                        if ((sFacet[ii] == noData) && (rFacet[i] == (i * pi / 4))) {          // If the downslope direction is on the other border of the current triangular facet, and the corresponding neigbour// s downslope is NoData
                            valley[i] = sFacet[i];
                        }
                    }
                }

                valleySum = valleySum + Math.pow(valley[i], hExp);
                if (valleyMax < valley[i]) {
                    iMax = i;
                    valleyMax = valley[i];
                }
            }

            // Compute the proportional contribution for each of the triangular facets
            if (valleySum > 0) {
                if (hExp < 10) {
                    for (i = 0; i < 8; i++) {
                        valley[i] = (Math.pow(valley[i], hExp)) / valleySum;
                        portion[i] = 0;
                    }
                } else {
                    for (i = 0; i < 8; i++) {
                        if (i != iMax) {
                            valley[i] = 0;
                        } else {
                            valley[i] = 1;
                        }
                        portion[i] = 0;
                    }
                }

                if (rFacet[7] == 0) {
                    rFacet[7] = 2 * pi;
                }

                // Compute the contribution to each of the neighbouring gridcells
                for (c = 0; c < 8; c++){
                    i = c;
                    ii = (i + 1) % 8;

                    if (valley[i] > 0) {
                        portion[i] = portion[i] + valley[i] * ((i + 1) * pi / 4 - rFacet[i]) / (pi / 4);
                        portion[ii] = portion[ii] + valley[i] * (rFacet[i] - (i) * pi / 4) / (pi / 4);
                    }
                }

                // Apply the flow accumulation to each of the neighbouring gridcells
                for (c = 0; c < 8; c++){
                    if (portion[c] > 0) {
                        a = col + xd[c];
                        b = row + yd[c];

                        if (tmpArea.getValue(b, a) != noData) {
                            tmpArea.incrementValue(b, a, flowAccumVal * portion[c]);
                        }
                    }
                }
            }

            for (c = 0; c < 8; c++){
                a = col + xd[c];
                b = row + yd[c];

                z1 = dem.getValue(b, a);
                if ((z > z1) && (z1 != noData)) {
                    tmpCount.incrementValue(b, a, -1);
                    if (tmpCount.getValue(b, a) == 0) {
                        MDInfAccum(b, a, hExp, noData);
                    }
                }
            }
        } else { // Use a D8 method
            double slope;
            double maxSlope = Double.MIN_VALUE;
            int flowDir = 255;

            if (flowAccumVal != noData & caThreshold != noData) {
                if (output.getValue(row, col) != 0) {
                    output.setValue(row, col, 1);
                }
                tmpArea.setValue(row, col, caThreshold);
            }

            // Find the neighbour with the steepest slope
            for (c = 0; c < 8; c++){
                a = col + xd[c];
                b = row + yd[c];
                z1 = dem.getValue(b, a);
                if ((z > z1) && (z1 != noData)) {
                    slope = (z - z1) / dd[c];
                    if (slope > maxSlope) {
                        maxSlope = slope;
                        flowDir = c;
                    }
                }
            }

            // Update neighbours (actually only the steepest slope neighbour)
            for (c = 0; c < 8; c++){
                a = col + xd[c];
                b = row + yd[c];
                z1 = dem.getValue(b, a);
                if ((z > z1) && (z1 != noData)) {
                    if (c == flowDir & tmpArea.getValue(b, a) != noData) {
                        output.setValue(b, a, 0);    // Cell (b, a) receives water from the current creek cell
                        tmpArea.incrementValue(b, a, caThreshold);
                    }

                    tmpCount.incrementValue(b, a, -1);
                    if (tmpCount.getValue(b, a) == 0) {
                        MDInfAccum(b, a, hExp, noData);
                    }
                }
            }
        }
    }
}