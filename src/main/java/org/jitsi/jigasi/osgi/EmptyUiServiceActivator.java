package org.jitsi.jigasi.osgi;

import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import net.java.sip.communicator.service.gui.*;
import net.java.sip.communicator.service.gui.Container;
import net.java.sip.communicator.service.gui.account.*;
import net.java.sip.communicator.service.gui.event.*;
import net.java.sip.communicator.service.protocol.*;
import org.jitsi.jigasi.*;
import org.osgi.framework.*;

public class EmptyUiServiceActivator
    implements BundleActivator
{
    @Override
    public void start(BundleContext bundleContext) throws Exception
    {
        bundleContext.registerService(UIService.class, new UIService()
        {
            @Override
            public boolean isVisible()
            {
                return false;
            }

            @Override
            public void setVisible(boolean visible)
            {
            }

            @Override
            public Point getLocation()
            {
                return null;
            }

            @Override
            public void setLocation(int x, int y)
            {
            }

            @Override
            public Dimension getSize()
            {
                return null;
            }

            @Override
            public void setSize(int width, int height)
            {
            }

            @Override
            public void minimize()
            {
            }

            @Override
            public void maximize()
            {
            }

            @Override
            public void restore()
            {
            }

            @Override
            public void resize(int width, int height)
            {
            }

            @Override
            public void move(int x, int y)
            {
            }

            @Override
            public void bringToFront()
            {
            }

            @Override
            public void setMainWindowCanHide(boolean canHide)
            {
            }

            @Override
            public ExportedWindow getExportedWindow(WindowID windowID)
                throws IllegalArgumentException
            {
                return null;
            }

            @Override
            public ExportedWindow getExportedWindow(WindowID windowID,
                Object[] params)
                throws IllegalArgumentException
            {
                return null;
            }

            @Override
            public PopupDialog getPopupDialog()
            {
                return null;
            }

            @Override
            public Chat getChat(Contact contact)
            {
                return null;
            }

            @Override
            public Chat getChat(Contact contact, String escapedMessageID)
            {
                return null;
            }

            @Override
            public Chat getChat(ChatRoom chatRoom)
            {
                return null;
            }

            @Override
            public List<Chat> getChats()
            {
                return null;
            }

            @Override
            public net.java.sip.communicator.service.contactlist.MetaContact getChatContact(
                Chat chat)
            {
                return null;
            }

            @Override
            public Chat getCurrentChat()
            {
                return null;
            }

            @Override
            public String getCurrentPhoneNumber()
            {
                return null;
            }

            @Override
            public void setCurrentPhoneNumber(String phoneNumber)
            {
            }

            @Override
            public SecurityAuthority getDefaultSecurityAuthority(
                ProtocolProviderService protocolProvider)
            {
                return new ServerSecurityAuthority(
                    protocolProvider,
                    protocolProvider.getAccountID().getPassword());
            }

            @Override
            public Iterator<WindowID> getSupportedExportedWindows()
            {
                return null;
            }

            @Override
            public boolean isExportedWindowSupported(WindowID windowID)
            {
                return false;
            }

            @Override
            public WizardContainer getAccountRegWizardContainer()
            {
                return null;
            }

            @Override
            public Iterator<Container> getSupportedContainers()
            {
                return null;
            }

            @Override
            public boolean isContainerSupported(Container containderID)
            {
                return false;
            }

            @Override
            public boolean useMacOSXScreenMenuBar()
            {
                return false;
            }

            @Override
            public ConfigurationContainer getConfigurationContainer()
            {
                return null;
            }

            @Override
            public CreateAccountWindow getCreateAccountWindow()
            {
                return null;
            }

            @Override
            public void addWindowListener(WindowListener l)
            {
            }

            @Override
            public void removeWindowListener(WindowListener l)
            {
            }

            @Override
            public Collection<Chat> getAllChats()
            {
                return null;
            }

            @Override
            public void addChatListener(ChatListener listener)
            {
            }

            @Override
            public void removeChatListener(ChatListener listener)
            {
            }

            @Override
            public void repaintUI()
            {
            }

            @Override
            public void createCall(String[] participants)
            {
            }

            @Override
            public void startChat(String[] participants)
            {
            }

            @Override
            public void startChat(String[] participants, boolean isSmsEnabled)
            {
            }

            @Override
            public ContactList createContactListComponent(
                ContactListContainer clContainer)
            {
                return null;
            }

            @Override
            public Collection<Call> getInProgressCalls()
            {
                return null;
            }

            @Override
            public LoginManager getLoginManager()
            {
                return null;
            }

            @Override
            public void openChatRoomWindow(
                net.java.sip.communicator.service.muc.ChatRoomWrapper chatRoom)
            {
            }

            @Override
            public void closeChatRoomWindow(
                net.java.sip.communicator.service.muc.ChatRoomWrapper chatRoom)
            {
            }

            @Override
            public void showAddChatRoomDialog()
            {
            }

            @Override
            public void showChatRoomAutoOpenConfigDialog(
                ProtocolProviderService pps, String chatRoomId)
            {
            }
        }, null);
    }

    @Override
    public void stop(BundleContext bundleContext) throws Exception
    {

    }
}
