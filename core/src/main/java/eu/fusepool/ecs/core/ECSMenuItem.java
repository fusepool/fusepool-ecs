package eu.fusepool.ecs.core;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Service;
import org.apache.stanbol.commons.web.base.NavigationLink;

@Component
@Service(NavigationLink.class)
public class ECSMenuItem extends NavigationLink {
    
    public ECSMenuItem() {
        super("ecs.core/", "ECS", "The Enhanced Content Store", 300);
    }
    
}
