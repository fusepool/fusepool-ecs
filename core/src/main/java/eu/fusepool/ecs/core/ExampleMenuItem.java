package eu.fusepool.ecs.core;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Service;
import org.apache.stanbol.commons.web.base.NavigationLink;

@Component
@Service(NavigationLink.class)
public class ExampleMenuItem extends NavigationLink {
    
    public ExampleMenuItem() {
        super("ecs.core/", "ECS", "An Example Service", 300);
    }
    
}
