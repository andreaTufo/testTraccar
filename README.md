# Simulazione di un veivolo su traccar

***
Nella soluzione del test proposta viene immaginato un veivolo dei vigili del fuoco che ha come compito estinguere un incendio. L'incendio è rappresentato dalla geofence nominata _"incendio"_. Il veivolo partirà nei pressi di Taranto da fermo e possiederà l'attributo custom _"droppingWater"_, che può assumere valori vero o falso.

## Istruzioni di esecuzione del codice

1. Una volta avviato traccar,visualizzare l'interfaccia modern ovvero _"http://{host}:{port}/modern/#/"_, in quanto su quella vecchia i device non compaiono.
2. Eseguire l'accesso come admin (user: **admin**, password: **admin**)
3. Aprire VScode o qualsiasi altro editor e mandare in esecuzione il file _"client.go"_.
4. Da terminale scegliere (digitando _"S"_ o _"N"_) se si vuole cambiare la base URL (quella di default è **http://localhost:8082**)
5. Se la scelta è stata no partirà subito la simulazione altrimenti verrà chiesto di digitare la nuova base URL (ad esempio digitando _"http://myserver.my:5001"_)
6. Ritornare su traccar ed aggiornare la pagina, il veivolo resterà inizialmente fermo per dare la possibilità di ingrandire in tempo sulla zona desiderata (nei pressi di Taranto) per poter vedere meglio ciò che accade.
7. Ingrandire quindi fino a che non si vedono sia il veivolo _"Vigili del fuoco"_ che la geofence _"incendio"_


## Nota bene

Il device si fermerà un paio di volte volontariamente per dare la possibilità di osservare gli attributi, cliccando prima sul device presente sulla mappa e poi su _"more info"_. La simulazione sarà finita quando su terminale comparirà la scritta _"finished"_.

## Notifiche

Una volta avviata la simulazone correttamente saranno ricevute notifiche ogni qual volta il dispositivo entra o esce dalla geofence.
