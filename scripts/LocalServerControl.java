import com.sun.tools.attach.VirtualMachine;
import javax.management.ObjectName;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Same-user local JMX only; no remote management port or HTTP shutdown endpoint. */
class LocalServerControl {
    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("Expected pid, instance token, timeout seconds");
        var task = CompletableFuture.runAsync(() -> {
            try {
                var vm = VirtualMachine.attach(args[0]);
                String address;
                try {
                    if (!args[1].equals(vm.getSystemProperties().getProperty("minipaintdex.launch.instance")))
                        throw new SecurityException("Instance identity mismatch; refusing shutdown");
                    address = vm.startLocalManagementAgent();
                } finally { vm.detach(); }
                try (var connector = JMXConnectorFactory.connect(new JMXServiceURL(address))) {
                    var connection = connector.getMBeanServerConnection();
                    var name = new ObjectName("org.springframework.boot:type=Admin,name=SpringApplication");
                    var identity = connection.invoke(name, "getProperty",
                            new Object[]{"minipaintdex.launch.instance"}, new String[]{"java.lang.String"});
                    if (!args[1].equals(identity)) throw new SecurityException("Spring instance identity mismatch");
                    connection.invoke(name, "shutdown", new Object[0], new String[0]);
                }
            } catch (Exception failure) { throw new IllegalStateException(failure); }
        });
        task.get(Long.parseLong(args[2]), TimeUnit.SECONDS);
    }
}
