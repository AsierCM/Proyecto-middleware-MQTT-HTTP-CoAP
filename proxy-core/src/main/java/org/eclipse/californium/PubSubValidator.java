/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.eclipse.californium.core;

import java.util.ArrayList;


/**
 * Assortment of different methods to check, adapt, manipulate and similar operations.
 * @author Thomas Wiss
 */
public class PubSubValidator {

    /**
     * Remove '/' in the front and in the back of the URI
     * @param path
     * @return adapted path as String or null if invalid
     */
    public static String adaptPath(String path) {
        if (!path.isEmpty()) {
            String updatedPath = path.trim();
            // no '/' in front or in back!
            updatedPath = updatedPath.startsWith("/") ? updatedPath.substring(1) : updatedPath;
            updatedPath = updatedPath.endsWith("/") ? updatedPath.substring(0, (updatedPath.length() - 1)) : path;
            if (validatePath(updatedPath)) {
                return updatedPath;
            } else {
                return null;
            }
        } else {
            return null;
        }

    }

    /**
     * Validate and check length of the URI
     * @param topicURI
     * @param uri
     * @return adapted URI as String or null if invalid/to short
     */
    public static String adaptURI(String topicURI, String uri) {
        if (!uri.isEmpty()) {
            String updatedURI = uri.trim();
            updatedURI = updatedURI.replace(topicURI, "");
            if (updatedURI.length() > 1 && validatePath(updatedURI)) {
                return updatedURI;
            } else {
                return null;
            }
        } else {
            return null;
        }

    }
    
    /**
     * Checks the path topics for proper length, validity and if not empty.
     * @param topics
     * @return String[] with the checked paths
     */
    public static String[] checkPathTopics(String[] topics){
        ArrayList<String> checkedTopics = new ArrayList<>();
        for(String str : topics){
            if(!str.isEmpty() && str.length() >= 1 && validatePath(str)){
                checkedTopics.add(str);
            }
        }
        return checkedTopics.toArray(new String[0]);
    }

    /**
     * Removes the leading 'ps' from the URI string
     * @param topicURI
     * @return string without leading 'ps'
     */
    public static String removePSfromURI(String topicURI) {
        if (!topicURI.isEmpty()) {
            String modifiedURI;
            modifiedURI = topicURI.startsWith("ps") ? topicURI.substring(2) : topicURI;
            return modifiedURI;
        } else {
            return null;
        }
    }
    
    /**
     * Check if URI starts with 'ps/'
     * @param topicURI
     * @return true if starts with 'ps/'
     */
    public static boolean uriStartsWithPS(String topicURI){
        if (!topicURI.isEmpty()) {
            return topicURI.startsWith("ps/");
        } else {
            return false;
        }
    }

    /**
     * Validate the path
     * @param path
     * @return true if valid
     */
    public static boolean validatePath(String path) {
        // INFO:
        // \\p{L} means a Unicode Character Property that matches any kind of letter from any language
        // plus extra symbols / and #
        // PS: - and ' are not allowd..
        String regex = "^[\\p{L}0-9 /#]+$";
        return path.matches(regex);
    }
    
    /**
     * Checks if ct is not smaller than 0
     * @param contentFormat
     * @return Integer which is bigger than or exactly 0
     */
    public static int validateContentFormat(int contentFormat) {
        return (contentFormat < 0) ? 0 : contentFormat;
    }

}
