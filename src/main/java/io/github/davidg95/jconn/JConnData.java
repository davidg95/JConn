/*
 * JConn networking framewotk.
 * Created by David Grant
 */
package io.github.davidg95.jconn;

import java.io.Serializable;
import java.util.HashMap;

/**
 * Object for storing data to be sent from client to server or vice-versa. The
 * flag indicates that the data is for. The data array contains any data that is
 * being sent.
 *
 * @author David
 */
public class JConnData implements Serializable, Cloneable {

    /**
     * The flag to indicate what the data is for.
     */
    private final String flag;
    /**
     * The array which contains any data for the transfer.
     */
    private final HashMap<String, Object> data;
    /**
     * The return value;
     */
    private Object returnValue;
    /**
     * The exception;
     */
    private Exception exception;
    /**
     * The return type for the object.
     */
    private int type;

    /**
     * Indicates that this object has parameters and is for a request.
     */
    public static final int REQUEST = 1;
    /**
     * Indicates that this object is a return value.
     */
    public static final int RETURN = 2;
    /**
     * Indicates that this object contains an exception from an error that
     * occurred.
     */
    public static final int EXCEPTION = 3;
    /**
     * Indicates an illegal parameter length.
     */
    public static final int ILLEGAL_PARAM_LENGTH = 4;

    /**
     * Constructor which creates a ConnectionData object with no data, only a
     * flag.
     *
     * @param flag the flag.
     */
    public JConnData(String flag) {
        this.flag = flag;
        this.data = new HashMap<>();
    }

    /**
     * Static method to create a ConnectionData object with a flag and no data.
     *
     * @param flag the flag to use.
     * @return the ConnectionData object.
     */
    public static JConnData create(String flag) {
        return new JConnData(flag);
    }

    /**
     * Method to get the flag for this object.
     *
     * @return the flag as a String.
     */
    public String getFlag() {
        return flag;
    }

    /**
     * Method to get the data for this object.
     *
     * @return the data as an Object array.
     */
    public HashMap getData() {
        return data;
    }

    /**
     * Get a parameter from the hashmap.
     *
     * @param param the parameter to get.
     * @return the object.
     */
    public Object getParam(String param) {
        return data.get(param);
    }

    /**
     * Adds a parameter to the JConnData.
     *
     * @param name the name of the parameter, must match the corresponding
     * parameter value on the server.
     * @param value the value of the parameter, must match the data type on the
     * server.
     * @return the JConnData object.
     */
    public JConnData addParam(String name, Object value) {
        data.put(name, value);
        type = REQUEST;
        return this;
    }

    /**
     * Sets the return value for this object.
     *
     * @param value the value to set.
     * @return this object.
     */
    public JConnData setReturnValue(Object value) {
        returnValue = value;
        type = RETURN;
        return this;
    }

    /**
     * Returns the return value indicated by the RETURN key. Same as
     * getData().getKey("RETURN").
     *
     * @return the return value.
     */
    public Object getReturnValue() {
        return returnValue;
    }

    /**
     * Sets the exception for this object.
     *
     * @param ex the exception.
     * @return this object.
     */
    public JConnData setException(Exception ex) {
        exception = ex;
        return this;
    }

    /**
     * Gets the exception for this object.
     *
     * @return the exception.
     */
    public Exception getException() {
        return exception;
    }

    /**
     * Returns the type of this object.
     *
     * @return the return type, can be either REQUEST, RETURN, EXCEPTION or
     * ILLEGAL_PARAM_LENGTH.
     */
    public int getType() {
        return type;
    }

    /**
     * Set the type for this data object. Can be REQUEST, RETURN, EXCEPTION or
     * ILLEGAL_PARAM_LENGTH.
     *
     * @param type the type.
     * @return this object.
     */
    public JConnData setType(int type) {
        this.type = type;
        return this;
    }

    /**
     * Method to clone the ConnectionData object.
     *
     * @return a copy of the object.
     * @throws CloneNotSupportedException if cloning is not supported.
     */
    @Override
    public JConnData clone() throws CloneNotSupportedException {
        try {
            final JConnData result = (JConnData) super.clone();
            return result;
        } catch (CloneNotSupportedException ex) {
            throw new AssertionError();
        }
    }

    /**
     * ToString method which displays the flag and how many data elements there
     * are.
     *
     * @return String representation of the object.
     */
    @Override
    public String toString() {
        return "Flag- " + this.flag
                + "\n Data- " + this.data.size();
    }
}
