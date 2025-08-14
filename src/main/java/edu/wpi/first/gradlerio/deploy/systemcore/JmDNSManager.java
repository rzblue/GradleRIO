package edu.wpi.first.gradlerio.deploy.systemcore;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Set;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceListener;

public class JmDNSManager implements AutoCloseable {
  private Set<JmDNS> instances = new HashSet<>();
  public static JmDNSManager createForAllInet4Addresses() throws IOException {
    JmDNSManager manager = new JmDNSManager();
    Set<InetAddress> allAddresses = new HashSet<>();
    var interfaces = NetworkInterface.getNetworkInterfaces();
    while (interfaces.hasMoreElements()) {
      var iface = interfaces.nextElement();
      if(!iface.isUp() || iface.isLoopback()) {
        continue;
      }
      if(!iface.supportsMulticast()) {
        continue;
      }
      for(var ifaceAddresses = iface.getInetAddresses(); ifaceAddresses.hasMoreElements();) {
        var address = ifaceAddresses.nextElement();
        if(!(address instanceof Inet4Address)) {
          continue;
        }
        if(address.isLoopbackAddress()) {
          continue;
        }
        allAddresses.add(address);
      }
    }
    for(var address : allAddresses) {
      if(address instanceof Inet4Address) {
        System.out.println("MANAGER: " + address);
        
        try {
          manager.instances.add(JmDNS.create(address));
        } catch(IOException e) {
          manager.close();
          throw new IOException("Exception occured while creating JmDNS on address '" + address + "'", e);
        } catch(Exception e) {
          e.printStackTrace();
          manager.close();
          return new JmDNSManager();
        }
      }
    }
    return manager;
  }

  public void addServiceListener(String type, ServiceListener listener) {
    for(var jmdns : instances) {
      jmdns.addServiceListener(type, listener);
    }
  }

  public void removeServiceListener(String type, ServiceListener listener) {
    for(var jmdns : instances) {
      jmdns.removeServiceListener(type, listener);
    }
  }

  public JmDNS[] getDNS() {
    return instances.toArray(new JmDNS[0]);
  }

  @Override
  public void close() {
    System.out.println("Closing manager!");
    for(var jmdns : instances) {
      try {
        jmdns.close();
      } catch (IOException ignored) {
        ignored.printStackTrace();
      }
    }
  }

  
}