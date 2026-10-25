/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package Cliente_Servidor.V1.Modelos;

import Cliente_Servidor.V1.Enums.TipoUsuario;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
/**
 *
 * @author crism
 */
@Entity
@Table(name = "usuarios_normales")
public class UsuarioNormal extends Usuario{
    
    private UsuarioNormal(){
        this.setTipoUsuario(TipoUsuario.NORMAL);
    }
    
    public static class Builder extends Usuario.Builder<Builder> {

        public Builder() {
            this.usuario = new UsuarioNormal();
        }

        @Override
        protected Builder self() {
            return this;
        }
    }
}
