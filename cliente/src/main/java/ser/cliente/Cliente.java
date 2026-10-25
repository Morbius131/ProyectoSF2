/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 */

package ser.cliente;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import ser.cliente.dao.DAO;
import ser.cliente.gui.GUIController;
import ser.cliente.view.ClientView;

public class Cliente {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            setLookAndFeel();
            DAO.initialize();

            ClientView view = new ClientView();
            new GUIController(view);
            view.setVisible(true);
        });
    }

    private static void setLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }
    }
}
