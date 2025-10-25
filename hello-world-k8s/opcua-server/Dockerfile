# Copy entrypoint
COPY entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh

# Copy XML template
COPY server-config.xml.template /app/server-config.xml.template

# Set entrypoint
ENTRYPOINT ["/app/entrypoint.sh"]

# Start your Java server (adjust if needed)
CMD ["java", "-jar", "server.jar"]
