/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package Cliente_Servidor.V1.DTO;

/**
 *
 * @author crism
 */
public class ResponseDTO {
    private String status;
    private int statusCode;
    private Object data;
    private String message;

    public ResponseDTO() {}
    
    public String getStatus() { return status; }
    public int getStatusCode() {return statusCode;}
    public Object getData() { return data; }
    public String getMessage() { return message; }

    public static ResponseDTO ok(Object data) {
        ResponseDTO r = new ResponseDTO();
        r.status = "OK";
        r.data = data;
        r.statusCode = 200;
        return r;
    }

    public static ResponseDTO error(String msg) {
        ResponseDTO r = new ResponseDTO();
        r.status = "ERROR";
        r.message = msg;
        r.statusCode = 400;
        return r;
    }
}
