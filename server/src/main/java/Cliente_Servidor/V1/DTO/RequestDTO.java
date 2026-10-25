/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package Cliente_Servidor.V1.DTO;

import Cliente_Servidor.V1.Enums.MethodHTTP;
import Cliente_Servidor.V1.Enums.TipoPeticion;
import java.util.Map;

/**
 *
 * @author crism
 */
public class RequestDTO {
    private TipoPeticion type;
    private MethodHTTP method;
    private Map<String, Object> data;

    public RequestDTO() {}

    public TipoPeticion getType() {
        return type;
    }

    public void setType(TipoPeticion type) {
        this.type = type;
    }

    public MethodHTTP getMethod() {
        return method;
    }

    public void setMethod(MethodHTTP method) {
        this.method = method;
    }
    
    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data;
    }

     
    
}
