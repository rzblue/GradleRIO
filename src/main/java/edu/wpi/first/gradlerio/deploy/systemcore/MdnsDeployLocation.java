package edu.wpi.first.gradlerio.deploy.systemcore;

import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;

import javax.inject.Inject;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;

import edu.wpi.first.deployutils.deploy.target.RemoteTarget;
import edu.wpi.first.deployutils.deploy.target.discovery.action.DiscoveryAction;
import edu.wpi.first.deployutils.deploy.target.discovery.action.SshDiscoveryAction;
import edu.wpi.first.deployutils.deploy.target.location.SshDeployLocation;
import edu.wpi.first.deployutils.log.ETLogger;
import edu.wpi.first.deployutils.log.ETLoggerFactory;

public class MdnsDeployLocation extends SshDeployLocation {

    @Inject
    public MdnsDeployLocation(String name, RemoteTarget target) {
        super(name, target);
    }

    private Optional<String> cachedAddress = Optional.empty();
    private ETLogger log = ETLoggerFactory.INSTANCE.create("MdnsDeployLocation");
    private int timeout = 4000;

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    @Override
    public String getAddress() {
        return cachedAddress.orElseGet(this::determineAddress);
    }

    @Override
    public DiscoveryAction createAction() {
        return new SshDiscoveryAction(this);
    }

    private String determineAddress() {
        System.out.println("NO CACHE, RESOLVING");
        String address = "";
        try {
            var manager = JmDNSManager.createForAllInet4Addresses();
            var listener = new OneShotResolverListener();
            manager.addServiceListener("_ni._tcp.local.", listener);
            log.log("timeout: " + timeout);
            var info = listener.waitForInfo(timeout);
            if(info != null) {
                address = info.getHostAddresses()[0];
                log.log("Resolved info: " + String.format("[name=%s, addresses=%s]", info.getName(), Arrays.toString(info.getHostAddresses())));
            } else {
                log.log("Discovery timed out or was interrupted");
            }
            new Thread(() -> manager.close()).start();
        } catch(IOException e) {
            e.printStackTrace();
        }
        return address;
    }

    private class OneShotResolverListener implements ServiceListener {
        ServiceInfo m_info;
        @Override
        public void serviceAdded(ServiceEvent event) {
            if(m_info != null) {
                return;
            }
            log.log("[listener] Found FRC controller service: " + event.getInfo().getName());
        }

        @Override
        public void serviceRemoved(ServiceEvent event) {
            if(m_info != null) {
                return;
            }
            log.log("[listener] Service removed: " + event.getInfo());
        }

        @Override
        public void serviceResolved(ServiceEvent event) {
            if(m_info != null) {
                return;
            }
            log.log("[listener] Resolved FRC controller IPs: " + "{" + event.getInfo().getName() + ": " + Arrays.toString(event.getInfo().getHostAddresses()) + "}");
            synchronized(this) {
                log.log("[listener] Notifying waiters!");
                this.m_info = event.getInfo();
                notifyAll();
            }
        }

        public ServiceInfo getInfo() {
            return m_info;
        }

        public ServiceInfo waitForInfo(long ms) {
            // Fast path check
            if(m_info == null) {
                // Lock the object so we can wait
                synchronized(this) {
                    // Check again, if it's been updated, don't wait
                    if(m_info == null) {
                        try {
                           wait(ms);
                        } catch(InterruptedException ignore) {
                            log.log("mDNS discovery interrupted, cancelling");
                        }
                    }
                }
            }
            return m_info;
        }
    }

}
