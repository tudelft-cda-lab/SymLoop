#!/bin/bash
docker start aistr
docker exec -it aistr sh -c "cd /home/str/JavaInstrumentation && /bin/bash"

