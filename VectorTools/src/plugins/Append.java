/*
 * Copyright (C) 2011-2012 Dr. John Lindsay <jlindsay@uoguelph.ca>
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

import java.io.File;
import whitebox.geospatialfiles.ShapeFile;
import whitebox.geospatialfiles.shapefile.attributes.DBFField;
import whitebox.geospatialfiles.shapefile.attributes.DBFWriter;
import whitebox.geospatialfiles.shapefile.ShapeFileRecord;
import whitebox.geospatialfiles.shapefile.ShapeType;
import whitebox.interfaces.WhiteboxPlugin;
import whitebox.interfaces.WhiteboxPluginHost;

/**
 * WhiteboxPlugin is used to define a plugin tool for Whitebox GIS.
 *
 * @author Dr. John Lindsay <jlindsay@uoguelph.ca>
 */
public class Append implements WhiteboxPlugin {
    
    private WhiteboxPluginHost myHost = null;
    private String[] args;
    
    /**
     * Used to retrieve the plugin tool's name. This is a short, unique name
     * containing no spaces.
     *
     * @return String containing plugin name.
     */
    @Override
    public String getName() {
        return "Append";
    }

    /**
     * Used to retrieve the plugin tool's descriptive name. This can be a longer
     * name (containing spaces) and is used in the interface to list the tool.
     *
     * @return String containing the plugin descriptive name.
     */
    @Override
    public String getDescriptiveName() {
    	return "Append";
    }

    /**
     * Used to retrieve a short description of what the plugin tool does.
     *
     * @return String containing the plugin's description.
     */
    @Override
    public String getToolDescription() {
    	return "Appends multiple vectors of the same type together";
    }

    /**
     * Used to identify which toolboxes this plugin tool should be listed in.
     *
     * @return Array of Strings.
     */
    @Override
    public String[] getToolbox() {
    	String[] ret = { "VectorTools" };
    	return ret;
    }

    /**
     * Sets the WhiteboxPluginHost to which the plugin tool is tied. This is the
     * class that the plugin will send all feedback messages, progress updates,
     * and return objects.
     *
     * @param host The WhiteboxPluginHost that called the plugin tool.
     */
    @Override
    public void setPluginHost(WhiteboxPluginHost host) {
        myHost = host;
    }

    /**
     * Used to communicate feedback pop-up messages between a plugin tool and
     * the main Whitebox user-interface.
     *
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
     * Used to communicate a return object from a plugin tool to the main
     * Whitebox user-interface.
     *
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
     * Used to communicate a progress update between a plugin tool and the main
     * Whitebox user interface.
     *
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
     * Used to communicate a progress update between a plugin tool and the main
     * Whitebox user interface.
     *
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
     *
     * @param args
     */
    @Override
    public void setArgs(String[] args) {
        this.args = args.clone();
    }
    
    private boolean cancelOp = false;
   
    /**
     * Used to communicate a cancel operation from the Whitebox GUI.
     *
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
     *
     * @return a boolean describing whether or not the plugin is actively being
     * used.
     */
    @Override
    public boolean isActive() {
        return amIActive;
    }

    @Override
    public void run() {
        
        amIActive = true;
        String inputFilesString = null;
        String[] shapefiles;
        int numFiles;
        String outputFile;
        String shortFileName;
        int progress;
        int i, n, FID;
        int oneHundredthTotal;
        int numRecs;
        ShapeType shapeType;
        ShapeType outputShapeType = ShapeType.POLYGON;
            
        if (args.length <= 0) {
            showFeedback("Plugin parameters have not been set.");
            return;
        }
        
        inputFilesString = args[0];
        outputFile = args[1];
        
        // check to see that the inputHeader and outputHeader are not null.
        if ((inputFilesString == null) || (outputFile == null)) {
            showFeedback("One or more of the input parameters have not been set properly.");
            return;
        }
        
        shapefiles = inputFilesString.split(";");
        numFiles = shapefiles.length;
        if (numFiles < 2) {
            showFeedback("At least two input vector files must be specified.");
            return;
        }

        try {
            
            ShapeFile output = null;
            DBFWriter writer = null;
            
            FID = 0;
                
            for (int k = 0; k < numFiles; k++) {
                
                ShapeFile input = new ShapeFile(shapefiles[k]);
                shapeType = input.getShapeType();
                numRecs = input.getNumberOfRecords();
                shortFileName = input.getShortName(); 
                //shortFileName cannot be longer than 25 characters
                if (shortFileName.length() > 25) {
                    String tempString = shortFileName.substring(0, 24);
                    shortFileName = tempString;
                }
                
                if (k == 0) {
                    outputShapeType = shapeType;
                    output = new ShapeFile(outputFile, outputShapeType);
                    output.setProjectionStringFromOtherShapefile(new ShapeFile(shapefiles[0]));

                    DBFField fields[] = new DBFField[3];

                    fields[0] = new DBFField();
                    fields[0].setName("FID");
                    fields[0].setDataType(DBFField.DBFDataType.NUMERIC);
                    fields[0].setFieldLength(10);
                    fields[0].setDecimalCount(0);
                    
                    fields[1] = new DBFField();
                    fields[1].setName("PARENTFILE");
                    fields[1].setDataType(DBFField.DBFDataType.STRING);
                    fields[1].setFieldLength(25);
                    
                    fields[2] = new DBFField();
                    fields[2].setName("PFILE_ID");
                    fields[2].setDataType(DBFField.DBFDataType.NUMERIC);
                    fields[2].setFieldLength(10);
                    fields[2].setDecimalCount(0);
                    
                    String DBFName = output.getDatabaseFile();
                    writer = new DBFWriter(new File(DBFName));

                    writer.setFields(fields);
                }
                
                if (shapeType != outputShapeType) {
                    showFeedback("This tool can only work when each input shapefile "
                            + "is of the same type.");
                    return;
                }
                
                oneHundredthTotal = numRecs / 100;
                progress = 0;
                n = 0;
                for (ShapeFileRecord record : input.records) {
                    output.addRecord(record.getGeometry());
                    
                    FID++;
                    Object[] rowData = new Object[3];
                    rowData[0] = new Double(FID);
                    rowData[1] = shortFileName;
                    rowData[2] = new Double(k + 1);
                    writer.addRecord(rowData);
                    
                    n++;
                    if (n >= oneHundredthTotal) {
                        n = 0;
                        if (cancelOp) {
                            cancelOperation();
                            return;
                        }
                        progress++;
                        updateProgress("Saving output file", progress);
                    }
                }
            }
            
            output.write();
            writer.write();
            
            
            // returning a header file string displays the image.
            returnData(outputFile);
            
            
        } catch (OutOfMemoryError oe) {
            myHost.showFeedback("An out-of-memory error has occurred during operation.");
        } catch (Exception e) {
            myHost.showFeedback("An error has occurred during operation. See log file for details.");
            myHost.logException("Error in " + getDescriptiveName(), e);
        } finally {
            updateProgress("Progress: ", 0);
            // tells the main application that this process is completed.
            amIActive = false;
            myHost.pluginComplete();
        }
       
    }
    
//    // This method is only used during testing.
//    public static void main(String[] args) {
//        args = new String[3];
////        args[0] = "/Users/johnlindsay/Documents/Data/ShapeFiles/NTDB_roads_rmow.shp"
////                + ";/Users/johnlindsay/Documents/Data/ShapeFiles/Water_Line_rmow.shp"
////                + ";/Users/johnlindsay/Documents/Data/ShapeFiles/Water_Body_rmow.shp";
////        args[1] = "/Users/johnlindsay/Documents/Data/ShapeFiles/tmp1.shp";
//
////        args[0] = "/Users/johnlindsay/Documents/Data/ShapeFiles/someLakes.shp"
////                + ";/Users/johnlindsay/Documents/Data/ShapeFiles/tmp5.shp";
////        args[1] = "/Users/johnlindsay/Documents/Data/ShapeFiles/tmp1.shp";
//        
////        args[0] = "/Users/johnlindsay/Documents/Data/ShapeFiles/rondeau lakes.shp"
////                + ";/Users/johnlindsay/Documents/Research/Conference Presentations and Guest Talks/2012 CGU/Data/rivers.shp";
////        args[1] = "/Users/johnlindsay/Documents/Data/ShapeFiles/tmp2.shp";
//        
//        args[0] = "/Users/johnlindsay/Documents/Data/ShapeFiles/tmp1.shp"
//                + ";/Users/johnlindsay/Documents/Data/ShapeFiles/tmp2.shp";
//        args[1] = "/Users/johnlindsay/Documents/Data/ShapeFiles/tmp3.shp";
//        
//        Union u = new Union();
//        u.setArgs(args);
//        u.run();
//    }
}
